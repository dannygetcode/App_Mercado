#!/usr/bin/env bash
# Verificación del proyecto Mercado (docs/06-pruebas.md).
#   ./scripts/verificar.sh rapido     pruebas de :domain + compilación debug
#   ./scripts/verificar.sh completo   todo: pruebas, lint, assembleDebug, manifiesto y código
set -u
cd "$(dirname "$0")/.."

MODE="${1:-rapido}"
RESULTS=()
FAILED=0

pass() { RESULTS+=("OK    $1"); }
fail() { RESULTS+=("FALLA $1${2:+ — $2}"); FAILED=1; }

summary() {
    echo
    echo "================ Resumen verificar.sh ($MODE) ================"
    for r in "${RESULTS[@]}"; do echo "$r"; done
    if [ "$FAILED" -eq 0 ]; then echo "RESULTADO: VERDE"; else echo "RESULTADO: ROJO"; fi
    exit "$FAILED"
}

run_gradle() {
    local label="$1"; shift
    if ./gradlew --console=plain "$@"; then pass "$label"; else fail "$label" "gradle falló"; fi
}

case "$MODE" in
    rapido)
        run_gradle "gradle :domain:test :app:compileDebugKotlin" :domain:test :app:compileDebugKotlin
        summary
        ;;
    completo) ;;
    *) echo "Uso: $0 rapido|completo"; exit 2 ;;
esac

run_gradle "gradle pruebas + lint + assembleDebug + manifiesto release" \
    :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:processReleaseMainManifest

MANIFEST="app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml"

# S-01 / S-14: permisos del manifiesto fusionado de release contra la lista blanca de 05-seguridad.md.
WHITELIST="android.permission.CAMERA
android.permission.POST_NOTIFICATIONS
android.permission.VIBRATE
android.permission.USE_BIOMETRIC
android.permission.USE_FINGERPRINT
android.permission.RECEIVE_BOOT_COMPLETED
android.permission.WAKE_LOCK
android.permission.FOREGROUND_SERVICE
co.daniel.mercado.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

if [ ! -f "$MANIFEST" ]; then
    fail "manifiesto fusionado de release" "no existe $MANIFEST"
else
    PERMS=$(tr '\n' ' ' < "$MANIFEST" | grep -oE '<uses-permission(-sdk-23)?[^>]*android:name="[^"]+"' \
        | grep -oE 'android:name="[^"]+"' | sed -E 's/android:name="([^"]+)"/\1/' | sort -u)
    EXTRA=$(comm -23 <(echo "$PERMS" | sed '/^$/d') <(echo "$WHITELIST" | sort -u))
    if [ -z "$EXTRA" ]; then pass "permisos en lista blanca (S-01, S-14)"
    else fail "permisos en lista blanca (S-01, S-14)" "fuera de lista: $(echo $EXTRA)"; fi

    # D-07 / L3-T6: ML Kit está dentro del APK y aun así no hay ningún permiso de red.
    if ! grep -q 'com.google.mlkit' "$MANIFEST"; then
        fail "ML Kit sin permisos de red (D-07)" "ML Kit no aparece en el manifiesto fusionado"
    elif grep -qE 'android.permission.(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE)"' "$MANIFEST"; then
        fail "ML Kit sin permisos de red (D-07)" "hay un permiso de red"
    else pass "ML Kit sin permisos de red (D-07)"; fi

    if grep -qE 'android:allowBackup="false"' "$MANIFEST"; then pass "allowBackup=false (S-04)"
    else fail "allowBackup=false (S-04)"; fi

    if grep -qE 'android:debuggable="true"' "$MANIFEST"; then fail "release no depurable (S-11)"
    else pass "release no depurable (S-11)"; fi

    # S-11: el único componente exportado es la actividad del lanzador.
    EXPORTED=$(tr '\n' ' ' < "$MANIFEST" | grep -oE '<(activity|activity-alias|service|receiver|provider)[^>]*android:exported="true"[^>]*>' \
        | grep -oE 'android:name="[^"]+"' | sed -E 's/android:name="([^"]+)"/\1/')
    if [ "$EXPORTED" = "co.daniel.mercado.MainActivity" ]; then pass "solo el lanzador exportado (S-11)"
    else fail "solo el lanzador exportado (S-11)" "exportados: $(echo $EXPORTED)"; fi
fi

# S-10: sin Log. ni println( fuera de AppLog.kt.
HITS=$(grep -rnE '\bLog\.|println\(' app/src/main domain/src/main --include='*.kt' --include='*.java' \
    | grep -v '/AppLog\.kt:' || true)
if [ -z "$HITS" ]; then pass "sin Log./println fuera de AppLog (S-10)"
else fail "sin Log./println fuera de AppLog (S-10)" "$(echo "$HITS" | head -3 | tr '\n' ' ')"; fi

# D-02: dominio puro.
HITS=$(grep -rnE '^\s*import\s+androidx?\.' domain/src/main || true)
if [ -z "$HITS" ]; then pass "dominio sin android/androidx (D-02)"
else fail "dominio sin android/androidx (D-02)" "$(echo "$HITS" | head -3 | tr '\n' ' ')"; fi

# D-05: sin Float/Double en model y rules del dominio.
DOM="domain/src/main/kotlin/co/daniel/mercado/domain"
HITS=$(grep -rnE '\b(Float|Double)\b|\.to(Float|Double)\(' "$DOM/model" "$DOM/rules" 2>/dev/null || true)
if [ -z "$HITS" ]; then pass "sin Float/Double en model/rules (D-05)"
else fail "sin Float/Double en model/rules (D-05)" "$(echo "$HITS" | head -3 | tr '\n' ' ')"; fi

# Migraciones: prohibido el borrado destructivo.
HITS=$(grep -rn 'fallbackToDestructiveMigration' app/src domain/src || true)
if [ -z "$HITS" ]; then pass "sin fallbackToDestructiveMigration"
else fail "sin fallbackToDestructiveMigration" "$(echo "$HITS" | head -3 | tr '\n' ' ')"; fi

# S-15: solo Google Maven y Maven Central (ni otros repositorios ni versiones dinámicas).
HITS=$(grep -nE '^\s*(gradlePluginPortal|jcenter|mavenLocal|maven)\b' settings.gradle.kts build.gradle.kts ./*/build.gradle.kts 2>/dev/null || true)
HITS="$HITS$(grep -nE '=\s*"[^"]*[+][^"]*"' gradle/libs.versions.toml || true)"
if [ -z "$HITS" ]; then pass "solo Google Maven y Maven Central, versiones fijas (S-15)"
else fail "solo Google Maven y Maven Central, versiones fijas (S-15)" "$(echo "$HITS" | head -3 | tr '\n' ' ')"; fi

summary
