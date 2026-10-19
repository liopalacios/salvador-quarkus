#!/usr/bin/env bash
# =============================================================================
# Salvador — Deploy del backend (API) a Azure Container Apps
# Requiere la infraestructura de configuraconAzure/salvador-azure-mvp.sh ya creada.
#
# Uso (Cloud Shell o local con az login), desde la raíz del repo:
#   bash deploy/azure-deploy.sh            # compila la imagen en ACR (az acr build)
#   LOCAL_DOCKER=1 bash deploy/azure-deploy.sh   # compila con Docker local y hace push
#
# Re-ejecutable: cada corrida publica una imagen nueva y crea una revisión nueva.
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

# ---- Mismos nombres que el script de infraestructura ----
SUBSCRIPTION_ID="${SUBSCRIPTION_ID:-$(az account show --query id -o tsv)}"
PREFIX="salvador"
ENV="mvp"
SUFFIX="$(echo -n "$SUBSCRIPTION_ID" | md5sum | cut -c1-6)"
RG="rg-${PREFIX}-${ENV}"
ACR="acr${PREFIX}${SUFFIX}"
KV="kv-${PREFIX}-${SUFFIX}"
ID="id-${PREFIX}-${ENV}"
API="ca-${PREFIX}-api"

IMAGE_NAME="salvador-api"
TAG="${TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo manual)-$(date +%Y%m%d%H%M%S)}"
IMAGE="${ACR}.azurecr.io/${IMAGE_NAME}:${TAG}"

step() { echo; echo "==> $*"; }

az account set --subscription "$SUBSCRIPTION_ID"
KV_URI=$(az keyvault show -g "$RG" -n "$KV" --query properties.vaultUri -o tsv)
ID_RES=$(az identity show -g "$RG" -n "$ID" --query id -o tsv)

# ---- 1. Secreto JWT en Key Vault (se crea una sola vez) ----
step "Secreto JWT en Key Vault"
if az keyvault secret show --vault-name "$KV" -n jwt-secret -o none 2>/dev/null; then
  echo "jwt-secret ya existe"
else
  az keyvault secret set --vault-name "$KV" -n jwt-secret --value "$(openssl rand -hex 32)" -o none
  echo "jwt-secret creado"
fi

# ---- 2. Imagen ----
step "Construyendo imagen ${IMAGE}"
if [[ "${LOCAL_DOCKER:-0}" == "1" ]]; then
  az acr login -n "$ACR"
  docker build --platform linux/amd64 -t "$IMAGE" .
  docker push "$IMAGE"
else
  # Compila en Azure: no necesita Docker local
  az acr build -r "$ACR" -t "${IMAGE_NAME}:${TAG}" -t "${IMAGE_NAME}:latest" .
fi

# ---- 3. Container App ----
step "Configurando Container App ${API}"
az containerapp secret set -g "$RG" -n "$API" \
  --secrets "jwt-secret=keyvaultref:${KV_URI}secrets/jwt-secret,identityref:${ID_RES}" -o none

# La imagen temporal escuchaba en 80; Spring Boot escucha en 8080
az containerapp ingress update -g "$RG" -n "$API" --target-port 8080 -o none

step "Desplegando nueva revisión"
az containerapp update -g "$RG" -n "$API" \
  --image "$IMAGE" \
  --set-env-vars "JWT_SECRET=secretref:jwt-secret" \
  -o none

# ---- 4. Verificación ----
FQDN=$(az containerapp show -g "$RG" -n "$API" --query properties.configuration.ingress.fqdn -o tsv)
HEALTH="https://${FQDN}/api/v1/actuator/health"
step "Esperando que la API responda en ${HEALTH}"
for i in $(seq 1 30); do
  if curl -fsS "$HEALTH" 2>/dev/null; then
    echo; echo "API desplegada OK: https://${FQDN}/api/v1"; exit 0
  fi
  echo "  ...todavía no ($i/30)"; sleep 10
done

echo "La API no respondió. Revisa los logs:"
echo "  az containerapp logs show -g $RG -n $API --type console --tail 100"
exit 1
