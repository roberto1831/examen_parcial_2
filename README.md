# AgroFlow (Evaluación Conjunta – Microservicios + RabbitMQ + Kubernetes)

Arquitectura mínima funcional para cumplir la rúbrica:

- **central-svc** (Spring Boot + PostgreSQL + AMQP Publisher)
- **inventario-svc** (Node.js + MySQL + AMQP Consumer)
- **facturacion-svc** (Flask + MariaDB + AMQP Consumer + callback a Central)
- **docker-compose** para entorno local
- **Kubernetes manifests** (Secrets, ConfigMaps, PVCs, Deployments, Services, Ingress)

> Reemplaza `DOCKERHUB_USER` en manifests por tu usuario real al publicar imágenes.

## 1) Correr local con Docker Compose

```bash
cd docker
docker compose up -d --build
# RabbitMQ UI: http://localhost:15672 (guest/guest)
```

Carga datos de prueba (insumos) en MySQL:
```bash
docker exec -it $(docker ps -qf name=mysql) mysql -uinventario -pinventariopass inventariodb -e "INSERT INTO insumos (insumo_id,nombre_insumo,stock,categoria) VALUES (UUID(),'Semilla Arroz L-23',1000,'Semilla'),(UUID(),'Fertilizante N-PK',1000,'Fertilizante');"
```

Crear un agricultor y una cosecha:
```bash
# Agricultor
curl -X POST http://localhost:8080/agricultores -H "Content-Type: application/json" -d '{"nombre":"Juan Perez","finca":"La Esperanza","ubicacion":"-0.2,-78.5","correo":"juan@example.com"}'

# Copia el agricultor_id devuelto y úsalo aquí:
curl -X POST http://localhost:8080/cosechas -H "Content-Type: application/json" -d '{"agricultorId":"<UUID>","producto":"Arroz Oro","toneladas":12.5}'
```

Verifica:
- `inventario-svc` descuenta stock automáticamente
- `facturacion-svc` crea la factura y hace `PUT` a Central (estado `FACTURADA`)

## 2) Publicar imágenes en Docker Hub

Desde cada carpeta de servicio:
```bash
# central-svc
docker build -t DOCKERHUB_USER/central-svc:v1.0 central-svc
docker push DOCKERHUB_USER/central-svc:v1.0
docker tag DOCKERHUB_USER/central-svc:v1.0 DOCKERHUB_USER/central-svc:latest
docker push DOCKERHUB_USER/central-svc:latest
# Repite con inventario-svc y facturacion-svc
```

## 3) Kubernetes (minikube recomendado)

```bash
kubectl apply -f deploy/k8s/pvc.yaml
kubectl apply -f deploy/k8s/databases.yaml
kubectl apply -f deploy/k8s/rabbitmq.yaml
kubectl apply -f deploy/k8s/configmaps.yaml
kubectl apply -f deploy/k8s/secrets.yaml

# Edita central.yaml, inventario.yaml, facturacion.yaml reemplazando DOCKERHUB_USER
kubectl apply -f deploy/k8s/central.yaml
kubectl apply -f deploy/k8s/inventario.yaml
kubectl apply -f deploy/k8s/facturacion.yaml

# (Opcional) Ingress
kubectl apply -f deploy/k8s/ingress.yaml
```

Pruebas rápidas (port-forward si no usas Ingress):
```bash
kubectl port-forward svc/central-svc 8080:8080 &
kubectl port-forward svc/inventario-svc 8081:8081 &
kubectl port-forward svc/facturacion-svc 8082:8082 &
```

Luego repetir las llamadas `curl` del punto 1.

## 4) Evidencias para el PDF
Ejecuta y captura (o redirige a JSON):
```bash
kubectl get deploy,svc,pvc -o json > evidencias_k8s.json
kubectl get pods -o json > pods.json
```

## Notas
- Este esqueleto es **mínimo pero funcional**. Ajusta validaciones, modelos y seguridad según la rúbrica.
- Si te falta tiempo, prioriza que el **flujo end-to-end** funcione y que las imágenes estén en Docker Hub.
