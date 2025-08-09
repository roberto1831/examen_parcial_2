import express from 'express';
import bodyParser from 'body-parser';
import dotenv from 'dotenv';
import amqp from 'amqplib';
import mysql from 'mysql2/promise';

dotenv.config();
const app = express();
app.use(bodyParser.json());

const PORT = process.env.SERVER_PORT || 8081;
const MYSQL_URL = process.env.MYSQL_URL || 'mysql://inventario:inventariopass@mysql:3306/inventariodb';
const RABBIT_URL = process.env.RABBIT_URL || 'amqp://guest:guest@rabbitmq:5672';
const EXCHANGE = process.env.RABBIT_EXCHANGE || 'cosechas';
const QUEUE_INVENTARIO = process.env.QUEUE_INVENTARIO || 'cola_inventario';
const ROUTING_NUEVA = process.env.RABBIT_ROUTING_NEW || 'nueva';
const ROUTING_OK = process.env.RABBIT_ROUTING_OK || 'inventario_ok';

let pool;
let channel;
let connection;

// ---------------- DB ----------------
async function initDb() {
  pool = await mysql.createPool(MYSQL_URL);
  await pool.query(`CREATE TABLE IF NOT EXISTS insumos (
    insumo_id CHAR(36) PRIMARY KEY,
    nombre_insumo VARCHAR(100) UNIQUE NOT NULL,
    stock INT DEFAULT 0 CHECK (stock >= 0),
    unidad_medida VARCHAR(10) DEFAULT 'kg',
    categoria VARCHAR(30) NOT NULL,
    ultima_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  )`);
}

// ---------------- API ----------------
app.get('/health', (_req, res) => res.json({ status: 'ok' }));

app.post('/insumos', async (req, res) => {
  try {
    const { nombre_insumo, stock = 0, unidad_medida = 'kg', categoria = 'Generico' } = req.body;
    await pool.query(
      'INSERT INTO insumos (insumo_id, nombre_insumo, stock, unidad_medida, categoria) VALUES (UUID(), ?, ?, ?, ?)',
      [nombre_insumo, stock, unidad_medida, categoria]
    );
    res.status(201).json({ ok: true });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

app.get('/insumos', async (_req, res) => {
  const [rows] = await pool.query('SELECT * FROM insumos');
  res.json(rows);
});

app.put('/insumos/ajustar', async (req, res) => {
  try {
    const { nombre_insumo, delta } = req.body;
    await pool.query('UPDATE insumos SET stock = stock + ? WHERE nombre_insumo = ?', [delta, nombre_insumo]);
    res.json({ ok: true });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

// ---------------- RabbitMQ ----------------
async function initRabbit() {
  connection = await amqp.connect(RABBIT_URL);
  channel = await connection.createChannel();
  await channel.assertExchange(EXCHANGE, 'direct', { durable: true });

  // cola de entrada
  await channel.assertQueue(QUEUE_INVENTARIO, { durable: true });
  await channel.bindQueue(QUEUE_INVENTARIO, EXCHANGE, ROUTING_NUEVA);

  channel.consume(QUEUE_INVENTARIO, async (msg) => {
    if (!msg) return;
    try {
      const content = JSON.parse(msg.content.toString());
      const p = content.payload || {};
      const toneladas = Number(p.toneladas || 0);
      const cosecha_id = p.cosecha_id;

      // Fórmula: 5kg semilla/t y 2kg fertilizante/t
      const ajustes = [
        { nombre: 'Semilla Arroz L-23', delta: -(5 * toneladas) },
        { nombre: 'Fertilizante N-PK', delta: -(2 * toneladas) },
      ];
      for (const a of ajustes) {
        await pool.query('UPDATE insumos SET stock = stock + ? WHERE nombre_insumo = ?', [a.delta, a.nombre]);
      }

      // publicar confirmación
      const confirm = {
        evento: 'inventario_ajustado',
        cosecha_id,
        status: 'OK',
        timestamp: new Date().toISOString(),
      };
      channel.publish(EXCHANGE, ROUTING_OK, Buffer.from(JSON.stringify(confirm)), { persistent: true });

      channel.ack(msg);
    } catch (e) {
      console.error('Error procesando mensaje inventario:', e);
      channel.nack(msg, false, false);
    }
  });

  // cierre ordenado si Rabbit se cae
  connection.on('close', () => {
    console.error('Conexión RabbitMQ cerrada. Saliendo...');
    process.exit(1);
  });
}

// ---------------- Bootstrap ----------------
(async () => {
  await initDb();
  await initRabbit();
  app.listen(PORT, () => console.log('inventario-svc listening on ' + PORT));
})().catch((err) => {
  console.error(err);
  process.exit(1);
});

// ---------------- Shutdown ----------------
process.on('SIGTERM', async () => {
  try {
    if (channel) await channel.close();
    if (connection) await connection.close();
    if (pool) await pool.end();
  } finally {
    process.exit(0);
  }
});
