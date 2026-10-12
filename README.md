# Lumen Chat

App de chat para Android, em Kotlin + Jetpack Compose, que conversa com a API da Anthropic usando a sua própria chave. É um projeto independente e não oficial.

## Como abrir e rodar

1. Instale o **Android Studio** (versão recente) com o **JDK 17**.
2. Abra a pasta `LumenChat` (File → Open). Aguarde o Gradle sincronizar; ele baixa tudo sozinho.
3. Conecte um celular com depuração USB ou crie um emulador (Android 8.0 / API 26 ou superior).
4. Clique em **Run**.
5. No app, abra **Ajustes** e cole sua chave de API. Você cria uma em https://console.anthropic.com/settings/keys.

Para gerar um APK instalável: Build → Build Bundle(s) / APK(s) → Build APK(s).

## O que o app faz

- Respostas em **streaming**, com botão de parar e de gerar de novo
- **Histórico offline** (Room): buscar, renomear, fixar e apagar conversas, agrupadas por data
- Escolha de modelo por conversa (Sonnet, Opus, Haiku) ou ID personalizado
- Renderização de **Markdown**: títulos, listas, tabelas, citações, links, blocos de código com botão de copiar
- **Ditado por voz** e **leitura em voz alta** das respostas
- Recebe texto de outros apps pelo menu **Compartilhar**
- Compartilhar uma resposta ou a conversa inteira
- Instruções personalizadas, limite de tamanho da resposta
- Tema claro, escuro ou do sistema, com Material You opcional
- Chave de API guardada com criptografia (EncryptedSharedPreferences + Android Keystore)

## Estrutura

```
app/src/main/java/app/lumen/chat/
  MainActivity.kt, LumenApplication.kt, AppContainer.kt
  data/        modelos, ajustes, repositório
  data/api/    cliente da Messages API (streaming SSE com OkHttp)
  data/db/     Room: conversas e mensagens
  ui/          ViewModel, telas, Markdown, voz
  ui/theme/    cores e tema
```

## Observações

- O uso é cobrado na sua conta da Anthropic. Os IDs dos modelos ficam em `data/Models.kt`; se a Anthropic lançar novos, basta adicioná-los ali ou usar o campo de ID personalizado.
- As conversas ficam só no aparelho. O backup automático do Android está desligado de propósito, para a chave não ir para a nuvem.
- Conversas muito longas: o app envia as últimas 80 mensagens para caber no contexto.
- Não distribua um APK com a sua chave embutida. Aqui ela é digitada pelo usuário e nunca vai no código.

## Ideias para evoluir

Anexar imagens e PDFs, busca na web, pastas de conversas, widget de tela inicial, sincronização entre aparelhos, geração automática de título.
