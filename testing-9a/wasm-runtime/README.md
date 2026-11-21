# WebAssembly runtime

TeaVM 0.16.0 компилирует Java-ядро языка, `RunService` и `Mandelbrot` в `target/wasm/app.wasm`.
Класс `WasmRuntime` задаёт экспортируемый API через `@JSExport`.

Из корня проекта выполните `npm run build`, затем `npm start`.
Для проверки одного модуля: `mvn -f testing-9a/wasm-runtime/pom.xml package`.

`RunService` не использует сеть, потоки JVM или reflection. Отладчик получает память
через публичные методы VM. В браузере синхронный Wasm-вызов выполняется в Worker;
внешний watchdog может завершить его целиком.

`ParityFixtureTest` автоматически собирает исходные программы из существующих
JUnit-провайдеров и сохраняет результаты JVM в `target/parity-fixtures.json`.
`tests/wasm-runtime.test.mjs` запускает те же программы в настоящем скомпилированном
Wasm и сравнивает весь JSON: байт-код, ошибки, вывод, память, графику и снимки шагов.
Reflection используется только при извлечении тестовых данных на JVM, в Wasm не попадает.

Сборочные артефакты не коммитятся; воспроизводимая сборка и контрольная сумма модуля
формируются в `dist/build.json`. Все версии Maven-зависимостей закреплены.
