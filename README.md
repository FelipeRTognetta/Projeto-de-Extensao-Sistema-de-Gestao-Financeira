# Sistema de Gestão Financeira para Psicólogos

Aplicativo Android para gerenciamento financeiro de consultórios de psicologia. Permite o cadastro de pacientes, registro de consultas, controle de pagamentos e geração de relatórios, com armazenamento totalmente local e criptografado.

## Funcionalidades

- **Pacientes**: cadastro com dados de contato, status ativo/inativo, responsável financeiro
- **Consultas**: registro de sessões com data, horário, duração e observações
- **Pagamentos**: controle de recebimentos (pago/pendente) com vínculo opcional à consulta
- **Dashboard**: resumo financeiro com receita, pacientes ativos e saldo pendente
- **Exportação**: CSV com dados de pacientes, consultas e pagamentos; backup criptografado
- **Autenticação biométrica**: digital/reconhecimento facial com sessão de 15 minutos

## Stack

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Kotlin (JVM 17) |
| UI | Jetpack Compose + Material 3 |
| Banco de dados | Room 2.6.1 + SQLCipher 4.5.4 |
| Segurança | Android Keystore + Tink + BiometricPrompt |
| Build | Gradle, compileSdk 35, minSdk 30 |
| Testes | JUnit 4, Mockito, Espresso, JaCoCo |

## Requisitos

- Android Studio 2024.2+
- JDK 17+
- Dispositivo ou emulador com Android 11+ (API 30+)

## Configuração

```bash
git clone <url-do-repositorio>
```

Abra a pasta `android/` no Android Studio e aguarde a sincronização do Gradle.

## Comandos

```bash
# Build debug
./gradlew assembleDebug

# Instalar no dispositivo
./gradlew installDebug

# Testes unitários
./gradlew testDebugUnitTest

# Testes instrumentados (requer dispositivo/emulador)
./gradlew connectedDebugAndroidTest

# Relatório de cobertura (JaCoCo)
./gradlew testCoverage
# Relatório em: android/app/build/reports/jacoco/test/html/index.html
```

## Segurança e Privacidade

- Dados armazenados exclusivamente no dispositivo (sem nuvem)
- Banco de dados criptografado com AES-256-GCM via SQLCipher
- Chaves gerenciadas pelo Android Keystore (hardware TEE/StrongBox)
- Autenticação biométrica obrigatória na abertura e em operações sensíveis
