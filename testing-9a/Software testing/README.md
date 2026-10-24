# Учебный компилятор и стековая виртуальная машина

Проект разбирает небольшой императивный язык, сразу генерирует стековый
байт-код и исполняет его на виртуальной машине. AST и отдельный токенайзер
намеренно не используются: это однопроходный учебный компилятор.

Полная спецификация языка (лексика, типы, операторы, функции, сообщения об
ошибках, грамматика, байт-код, ограничения, примеры) — в корневом
[README.md](../../README.md). Здесь — только устройство проекта.

## Структура

```text
by.pranovich.testing
├── compiler   — внешний фасад Compiler и описание SyntaxError
├── parser     — рекурсивный спуск и проверка синтаксиса
├── bytecode   — инструкции, опкоды, Program и CodeGenerator
├── runtime    — стековая VirtualMachine и значение Complex
└── Main       — минимальный пример запуска
```

Поток выполнения:

```text
исходный текст → Compiler → RecursiveDescentParser → Program → VirtualMachine
```

## Кратко о языке

```text
int x = 1;                    // комментарии: // и /* ... */
float y = 2.5;
complex z = 3 + 4i;
int values[3];
int initialized = [1, 5, 2];
values[0] = initialized[1];

fun int fact(int n) { if n <= 1 { return 1; } return n * fact(n - 1); }

if x < 5 && y <> 0 {
    print(max(x, initialized[2]), fact(5), z * conj(z));   // 2 120 25.0+0.0i
} else {
    print(0);
}
while x < 10 {
    x = x + 1;
}
```

- Типы `int`, `float`, `complex` и одномерные массивы из них; `void` — только
  для результата функции.
- Встроенные функции: `sin`, `cos` (`cod` — совместимый псевдоним), `tg`, `ctg`,
  `abs`, `max`, `min`, `_pi`, `_e`, для комплексных чисел — `re`, `im`, `abs`,
  `conj`, `arg`. Пользовательские функции — `fun тип имя(параметры) { ... }`.
- Арифметика не возвращает неправильных значений: переполнение, `NaN`, деление
  на ноль, потеря точности при записи в `int` и выход `int` за `2^53 - 1` дают
  ошибку выполнения (`ArithmeticException` и др.), а не результат.

Подробности и полные списки ошибок — в [корневом README](../../README.md).

## Проверка

```shell
mvn test
```

Все тесты запускаются через единый сьют `by.pranovich.testing.AllTests`
(`src/test/java/by/pranovich/testing/AllTests.java`). Он автоматически находит
тестовые классы в пакете `by.pranovich.testing` и его подпакетах, поэтому новый
тест достаточно просто добавить в проект — регистрировать его в сьюте не нужно.

Сценарии разложены по параметризованным тестам в `src/test/java/by/pranovich/testing/parser`:
разбор выражений (`RecursiveDescentParserTest`), программы целиком с ожидаемым
выводом или ошибкой компиляции (`CompilerParamTest`), массивы (`ArrayParsingTest`, `ArrayOperationsTest`, `ArrayComparisonAndPrintTest`),
встроенные функции (`ArrayAndBuiltinFunctionTest`), логические выражения
(`LogicalExpressionTest`), `if`/`while` (`RecursiveDescentParserIfWhileElseTest`),
степень (`PowerOperatorTest`), комментарии (`CommentTest`), позиции исходника
для отладчика (`SourcePositionTest`), переполнение и потеря точности
(`OverflowAndPrecisionTest`), комплексные числа (`ComplexNumberTest`) и функции
(`FunctionTest`). Фасад `Compiler` проверяется в `compiler/CompilerTest`.
