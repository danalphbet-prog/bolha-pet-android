# Bolha Pet · Qwen2.5 1.5B Q4_K_M (3B opcional)

Novo aplicativo **Android** separado do EduAgent (`com.bolhapet.app`).

## O que foi construído

- Interface sem `ActionBar`/barra superior; uma bolha procedural animada ocupa o centro superior.
- Chat embaixo, campo de texto e botão de enviar com ícone **Lucide**.
- Bolha baseada no arquivo original `Bolha procedural.html` enviado pelo usuário.
- 24 expressões originais, piscadas, corpo flutuante, olhos acompanhando o dedo e boca animada durante o texto gerado.
- **A IA controla o humor**, usando o protocolo `[[emocao:curioso]]` no início de cada resposta, que é retirado do chat antes de mostrar o texto. Não é necessário escolher a emoção manualmente.
- Qwen2.5-1.5B-Instruct Q4_K_M como padrão, com Qwen2.5-3B-Instruct Q4_K_M opcional, via llama.cpp JNI nativo, local/offline após download; histórico recente limitado enviado como contexto em cada turno.
- Download retomável do modelo, verificação SHA-256 oficial e opção de importar o GGUF via seletor de arquivos do Android.
- 1.903 ícones Lucide SVG salvos em `app/src/main/assets/icons/`, com licença incluída em `LUCIDE_LICENSE`.
- Não há permissões de sobreposição, microfone ou acessibilidade nesta versão.

## Prévia visual

Abra `app/src/main/assets/pet.html` num navegador. **A prévia usa respostas simuladas**, apenas para testar animação e a interface. No **APK** o HTML usa `PetNative`, que encaminha as mensagens para Qwen real no celular.

## Modelo

- Origem 1.5B: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/blob/main/qwen2.5-1.5b-instruct-q4_k_m.gguf
- Origem 3B: https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/blob/main/qwen2.5-3b-instruct-q4_k_m.gguf
- Quantização: Q4_K_M. 1.5B = 1.117.320.736 bytes. 3B = 2.104.932.768 bytes.
- SHA-256 1.5B: `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`
- SHA-256 3B: `626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d`.
- Armazenamento: área privada do aplicativo (sem necessidade de acesso total a arquivos).
- Atenção: em aparelhos com RAM reduzida a carga da IA pode falhar por falta de memória.

## Compilação pelo GitHub Actions

O código do aplicativo é independente do EduAgent, mas reaproveita a **biblioteca JNI/llama.cpp** do próprio repositório `danalphbet-prog/eduagent-android` em um commit fixo durante o build. Isso evita copiar o aplicativo antigo, e dispensa embarcar arquivos gigantes no ZIP.

O workflow `.github/workflows/android-apk.yml`:
1. Clona este projeto e a biblioteca JNI estável (`8bcc07e8...`).
2. Clona llama.cpp no commit fixo `f174553e...`.
3. Prepara SDK 36, NDK 29 e CMake 3.31.6.
4. Executa testes e `:app:assembleDebug`.
5. Publica um artifact chamado `bolha-pet-debug-apk`.

Como é um projeto novo, crie um **repositório novo**, por exemplo `bolha-pet-android`, sem substituir `eduagent-android`. Faça commit e push no branch `main` ou rode o script de publicação em Termux descrito abaixo.

### Termux

Extraia este ZIP (com seu próprio conteúdo na raiz) no celular e execute:

```bash
pkg install -y git gh unzip
termux-setup-storage
mkdir -p ~/bolha-pet-android
cd ~/bolha-pet-android
unzip -oq /sdcard/Download/bolha-pet-qwen3b-android.zip
git init -b main
git add -A
git commit -m 'Novo Bolha Pet com Qwen 3B Q4 e Lucide'
gh repo create bolha-pet-android --public --source=. --remote=origin --push
```

Depois abra o Actions do novo repositório; aguarde o artifact `bolha-pet-debug-apk`.

### Compilação local (Android Studio / Linux)

Execute `bash scripts/bootstrap-runtime.sh` para obter a biblioteca JNI, fontes do llama.cpp e Gradle wrapper, depois `./gradlew :app:assembleDebug`. Requer SDK/NDK/CMake. O ZIP fonte, por si só, **não é um APK**.

## Segurança

A interface roda em `file:///android_asset/pet.html`, sem navegação para websites externos. A JavaScript bridge expõe apenas `send`, `downloadModel` e `importModel`, para páginas locais. Nenhuma informação da conversa é enviada para a nuvem. A internet só é usada para baixar os pesos e dependências durante a compilação.

## Estado dos testes

A estrutura do projeto, XML Android, JavaScript da prévia e integridade do ZIP são verificados localmente. **O APK e a inferência nativa precisam ser compilados e testados no GitHub Actions e no seu telefone.**

## Revisão de inicialização 0.2.0

- A tela principal aparece antes de tentar carregar a IA nativa; a UI funciona mesmo sem GGUF.
- Tratamento de exceções de abertura e falhas da WebView, com diagnóstico legível.
- Se a aplicação crashar por exceção Java/Kotlin, na próxima abertura mostra um diagnóstico com botão **Copiar diagnóstico**.
- A versão 0.2.0 inclui `BolhaPetApp`, mas não conecta contas nem lê dados do EduAgent: o `applicationId` e os diretórios de dados são distintos (`com.bolhapet.app`).
- A biblioteca JNI é atualmente obtida **como código-fonte durante o build** de um commit público do projeto anterior. Isso NÃO acessa os dados do outro aplicativo, mas é uma dependência de código ainda existente.
- A causa específica de um fechamento instantâneo da instalação antiga só pode ser confirmada com `adb logcat -b crash`.
- **Não desinstale a versão já instalada sem salvar os dados**, pois a desinstalação apaga a pasta privada com modelos e conversas.

### Capturar falha do APK antigo por ADB

Depois de ativar a depuração sem fio e parear o Termux com `adb pair`, execute:

```sh
adb shell am force-stop com.bolhapet.app
adb logcat -c
adb shell am start -n com.bolhapet.app/.MainActivity
sleep 3
adb logcat -d -b crash -v time | tail -n 120
```

Se não aparecer nada, use `adb logcat -d -v time | grep -A 35 -B 8 -E 'FATAL EXCEPTION|bolhapet|com.bolhapet.app' | tail -n 120`.
