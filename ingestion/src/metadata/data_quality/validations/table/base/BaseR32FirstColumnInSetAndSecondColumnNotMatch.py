import traceback
from abc import abstractmethod
from typing import cast
from metadata.data_quality.validations.base_test_handler import BaseTestValidator
from metadata.generated.schema.tests.basic import (
    TestCaseResult,
    TestCaseStatus,
    TestResultValue,
)
from metadata.utils.logger import test_suite_logger

logger = test_suite_logger()

ROW_COUNT = "Number of Rows code R32 ="


class BaseR32FirstColumnInSetAndSecondColumnNotMatchValidator(BaseTestValidator):
    """Validator for column values to be not null test case"""

    def run_validation(self) -> TestCaseResult:
        """Run validation for the given test case

        Returns:
            TestCaseResult:
        """
        first_column_name = self._get_first_column_name()
        second_column_name = self._get_second_column_name()
        test_first_column_values = self._get_test_first_column_values()
        test_second_column_regex = self._get_test_second_column_regex()

        try:
            first_column_name = cast(str, first_column_name)
            second_column_name = cast(str, second_column_name)
            test_first_column_values = cast(str, test_first_column_values)
            test_second_column_regex = cast(str, test_second_column_regex)
            logger.warning(
                f'self._run_results({first_column_name}, {second_column_name}, '
                f'{test_first_column_values},{test_second_column_regex})')
            res = self._run_results(first_column_name, second_column_name, test_first_column_values,
                                    test_second_column_regex)
        except Exception as exc:
            msg = f"Error computing {self.test_case.name}: {exc}"  # type: ignore
            logger.debug(traceback.format_exc())
            logger.warning(msg)
            return self.get_test_case_result_object(
                self.execution_date,
                TestCaseStatus.Aborted,
                msg,
                [TestResultValue(name=ROW_COUNT, value=None)],
            )

        return self.get_test_case_result_object(
            self.execution_date,
            self.get_test_case_status(res == 0),
            f"Number of Rows code R32 = {res}.",
            [TestResultValue(name=ROW_COUNT, value=str(res))],
        )

    @abstractmethod
    def _get_first_column_name(self):
        raise NotImplementedError

    @abstractmethod
    def _get_second_column_name(self):
        raise NotImplementedError

    @abstractmethod
    def _get_test_first_column_values(self):
        raise NotImplementedError

    @abstractmethod
    def _get_test_second_column_regex(self):
        raise NotImplementedError

    @abstractmethod
    def _run_results(self, fist_column_name: str, second_column_name: str,
                     test_fist_column_values: str, test_second_column_regex: str):
        raise NotImplementedError
