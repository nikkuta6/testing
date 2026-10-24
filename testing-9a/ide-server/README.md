# Веб-IDE (Ace Editor)

HTTP-сервер на встроенном в JDK `HttpServer`, без зависимостей. Компилятор и VM
берутся из исходников `../Software testing` (сам проект не меняется).

```shell
mvn compile exec:java            # нужен JDK 25, как и для Software testing
```

Открыть http://localhost:8080/ , запуск — кнопка ▶ или Ctrl+Enter.

## API

`POST /api/run`, тело — исходный текст программы.

Ответ при синтаксической ошибке:

```json
{"syntaxError": {"message": "...", "line": 2, "position": 9}}
```

Иначе:

```json
{"bytecode": "0000  PUSH 1.0\n...", "output": "...", "runtimeError": null,
 "variables": {"x": "2.5", "a": "[1.0, 2.0]"}, "stack": []}
```

Программа выполняется не дольше 3 секунд, вывод ограничен 1 МБ.
