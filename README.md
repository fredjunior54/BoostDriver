# BoostDriver

Amplificador de áudio para Android 6.0 ou superior, inspirado no funcionamento do Volume Booster GOODEV. **Sem anúncios, telemetria ou acesso à internet.**

## 🎚️ Como usar

- **Volume de mídia** ajusta o volume do sistema. **Amplificação** aplica ganho adicional onde o Android oferece suporte.
- Comece com volume baixo. **Parar**, no painel ou na notificação, desativa o efeito sem mudar o volume de mídia.
- O limite padrão é 40%; as configurações permitem alterá-lo, mostrar ou esconder o volume de mídia e optar pela retomada após reiniciar.

**Risco:** ganho adicional pode distorcer o áudio e causar danos à audição ou ao equipamento. Nenhum percentual é garantido como seguro.

## 🔊 Compatibilidade e permissões

O app tenta `LoudnessEnhancer` e depois `Equalizer` no mix global. Essa sessão é legada: alguns aparelhos, players e saídas de áudio podem rejeitar ou ignorar o efeito. O estado ativo indica que o Android aceitou o efeito, não que o ganho foi ouvido em todos os apps. Não é um amplificador de chamadas ou alarmes.

O serviço em primeiro plano mantém o ganho enquanto o painel está fechado. No Android 13+, a permissão de notificações permite mostrar o ganho e a ação **Parar** na gaveta. A permissão de inicialização é usada somente para retomar o ganho quando essa opção estiver habilitada.

## 🛠️ Compilação

Requer Android SDK 36 e JDK 17 ou mais recente compatível com o Gradle.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## License

GPL-3.0
