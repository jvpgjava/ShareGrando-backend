# ScreenGrando Backend

API Spring Boot do ScreenGrando. Não há conta ou login: uma sala é criada diretamente. As sessões e suas chaves ficam em memória neste primeiro corte; reiniciar o processo remove as salas.

## Requisitos

- Java 21+
- Maven 3.9+
- Um servidor LiveKit acessível pelo navegador e pelo backend

## Execução local

Inicie o LiveKit e depois execute:

```powershell
mvn spring-boot:run
```

Por padrão, a API usa `http://localhost:8080`, LiveKit em `ws://localhost:7880` e as chaves locais `devkey` / `devsecret-livekit-screengrando-32bytes!`. Em produção, configure `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` e `FRONTEND_BASE_URL`; a URL entregue aos navegadores deve ser pública e usar WSS.

## Fluxo das rotas

- `POST /api/sessions` cria uma sala sem limite de duração. `title` é obrigatório e `pin` opcional (4 a 12 dígitos). A sala fica ativa até ser encerrada manualmente.
- A resposta inclui `publisherSecret`. Guarde essa chave no cliente: ela permite solicitar tokens de apresentador e encerrar essa sala.
- `GET /api/sessions/{code}` e `POST /api/sessions/{code}/viewer-token` são públicos. Se houver PIN, ele é validado antes de emitir o token do espectador.
- `POST /api/sessions/{code}/publisher-token`, `POST /api/sessions/{code}/publisher-started` e `DELETE /api/sessions/{code}` exigem `X-Session-Secret` com a chave da criação.

Os tokens LiveKit são assinados com a chave configurada. A contagem de espectadores consulta `ListParticipants`; encerrar uma sala envia `DeleteRoom` para desconectar os participantes.
