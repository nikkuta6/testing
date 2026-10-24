package by.pranovich.testing;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Единая точка запуска всех тестов проекта.
 *
 * <p>Сьют автоматически находит все тестовые классы в пакете
 * {@code by.pranovich.testing} и его подпакетах, поэтому новые тесты
 * подключать вручную не нужно.</p>
 */
@Suite
@SuiteDisplayName("Все тесты проекта")
@SelectPackages("by.pranovich.testing")
@ExcludeClassNamePatterns(".*\\.AllTests")
public class AllTests {
}
