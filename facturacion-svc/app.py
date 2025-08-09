from flask import Flask, request, jsonify
import os, json, threading, time, requests, sys, signal, uuid
import pika
import pymysql

app = Flask(__name__)

# --------- Config ----------
PORT = int(os.getenv("SERVER_PORT", "8082"))
RABBIT_URL = os.getenv("RABBIT_URL", "amqp://guest:guest@rabbitmq:5672")
EXCHANGE = os.getenv("RABBIT_EXCHANGE", "cosechas")
ROUTING_OK = os.getenv("RABBIT_ROUTING_OK", "inventario_ok")  # clave correcta

CENTRAL_URL = os.getenv("CENTRAL_URL", "http://central-svc:8080")
MARIADB_HOST = os.getenv("MARIADB_HOST", "mariadb")
MARIADB_DB = os.getenv("MARIADB_DB", "facturadb")
MARIADB_USER = os.getenv("MARIADB_USER", "factura")
MARIADB_PASS = os.getenv("MARIADB_PASS", "facturapass")

PRICES = {"Arroz Oro": 120, "Cafe Premium": 300}

def log(*a): 
    print(*a, file=sys.stdout, flush=True)

# --------- DB ----------
def get_db():
    return pymysql.connect(
        host=MARIADB_HOST,
        user=MARIADB_USER,
        password=MARIADB_PASS,
        database=MARIADB_DB,
        autocommit=True,
    )

def init_db():
    conn = get_db()
    with conn.cursor() as cur:
        cur.execute(
            """CREATE TABLE IF NOT EXISTS facturas (
                factura_id CHAR(36) PRIMARY KEY,
                cosecha_id CHAR(36) NOT NULL,
                monto_total DECIMAL(10,2) NOT NULL,
                pagado BOOLEAN DEFAULT 0,
                fecha_emision TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )"""
        )
    conn.close()

# --------- API ----------
@app.post("/facturas")
def create_factura():
    data = request.json or {}
    cosecha_id = data.get("cosecha_id")
    producto = data.get("producto", "Arroz Oro")
    toneladas = float(str(data.get("toneladas", 0)).replace(",", "."))
    monto = toneladas * PRICES.get(producto, 100)

    conn = get_db()
    factura_id = str(uuid.uuid4())   # <-- UUID!
    with conn.cursor() as cur:
        cur.execute(
            "INSERT INTO facturas (factura_id, cosecha_id, monto_total, pagado) VALUES (%s,%s,%s,%s)",
            (factura_id, cosecha_id, monto, 0),
        )
    conn.close()
    log("[API] Factura creada", factura_id, "para cosecha", cosecha_id, "monto", monto)
    return jsonify({"factura_id": factura_id, "monto_total": monto})

@app.get("/health")
def health():
    return {"status": "ok"}

# --------- Helpers ----------
def fetch_cosecha(cosecha_id: str):
    """Obtiene producto y toneladas desde Central si el mensaje no los trae."""
    # 1) Intentar GET /cosechas/{id}
    try:
        r = requests.get(f"{CENTRAL_URL}/cosechas/{cosecha_id}", timeout=5)
        if r.status_code == 200:
            c = r.json()
            return {
                "producto": c.get("producto", "Arroz Oro"),
                "toneladas": float(str(c.get("toneladas", 0)).replace(",", ".")),
            }
    except Exception as e:
        log("fetch_cosecha: fallo /cosechas/{id} ->", e)
    # 2) Fallback GET /cosechas
    try:
        r = requests.get(f"{CENTRAL_URL}/cosechas", timeout=5)
        r.raise_for_status()
        for c in r.json():
            if c.get("cosechaId") == cosecha_id:
                return {
                    "producto": c.get("producto", "Arroz Oro"),
                    "toneladas": float(str(c.get("toneladas", 0)).replace(",", ".")),
                }
    except Exception as e:
        log("fetch_cosecha: fallo /cosechas ->", e)
    return {"producto": "Arroz Oro", "toneladas": 0.0}

# --------- RabbitMQ Consumer ----------
def rabbit_consumer():
    """Consumer con reintentos + logs claros."""
    while True:
        try:
            log("Conectando a RabbitMQ:", RABBIT_URL)
            params = pika.URLParameters(RABBIT_URL)
            connection = pika.BlockingConnection(params)
            channel = connection.channel()
            channel.exchange_declare(exchange=EXCHANGE, exchange_type="direct", durable=True)

            # Cola de facturación ligada a la confirmación de inventario
            result = channel.queue_declare(queue="cola_facturacion", durable=True)
            queue_name = result.method.queue
            channel.queue_bind(exchange=EXCHANGE, queue=queue_name, routing_key=ROUTING_OK)

            log("Facturacion consumer started (listening routing_key:", ROUTING_OK, ")")

            def callback(ch, method, properties, body):
                try:
                    message = json.loads((body or b"{}").decode())
                    payload = message.get("payload") or message
                    log("[RX] inventario_ok:", payload)

                    cosecha_id = payload.get("cosecha_id")
                    producto = payload.get("producto")
                    toneladas = payload.get("toneladas")

                    if not producto or toneladas is None:
                        info = fetch_cosecha(cosecha_id)
                        producto = producto or info["producto"]
                        toneladas = float(toneladas) if toneladas is not None else float(info["toneladas"])
                    else:
                        toneladas = float(str(toneladas).replace(",", "."))

                    monto = float(toneladas) * PRICES.get(producto, 100)

                    conn = get_db()
                    factura_id = str(uuid.uuid4())   # <-- UUID!
                    with conn.cursor() as cur:
                        cur.execute(
                            "INSERT INTO facturas (factura_id, cosecha_id, monto_total, pagado) VALUES (%s,%s,%s,%s)",
                            (factura_id, cosecha_id, monto, 0),
                        )
                    conn.close()
                    log("[DB] Factura", factura_id, "insertada. Monto:", monto)

                    try:
                        r = requests.put(
                            f"{CENTRAL_URL}/cosechas/{cosecha_id}/estado",
                            json={"estado": "FACTURADA", "factura_id": factura_id},
                            timeout=5,
                        )
                        log("[PUT] Central status:", r.status_code)
                    except Exception as e:
                        log("Error actualizando estado en Central:", e)

                    ch.basic_ack(delivery_tag=method.delivery_tag)
                except Exception as e:
                    log("Error procesando mensaje en facturacion:", e)
                    ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

            channel.basic_qos(prefetch_count=1)
            channel.basic_consume(queue=queue_name, on_message_callback=callback)
            channel.start_consuming()
        except Exception as e:
            log("Error en consumer de RabbitMQ, reintentando en 5s:", e)
            time.sleep(5)

def start_consumer_thread():
    t = threading.Thread(target=rabbit_consumer, daemon=True)
    t.start()

# --------- Main / Shutdown ----------
def handle_sigterm(*_):
    log("Recibido SIGTERM, saliendo...")
    os._exit(0)

if __name__ == "__main__":
    signal.signal(signal.SIGTERM, handle_sigterm)
    init_db()
    start_consumer_thread()
    app.run(host="0.0.0.0", port=PORT)

