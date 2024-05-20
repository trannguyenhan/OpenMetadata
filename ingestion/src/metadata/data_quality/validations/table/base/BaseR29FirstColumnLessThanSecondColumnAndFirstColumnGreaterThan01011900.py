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

ROW_COUNT = "Number of Rows code R29 ="


class BaseR29FirstColumnLessThanSecondColumnAndFirstColumnGreaterThan01011900Validator(BaseTestValidator):
    """Validator for column values to be not null test case"""


    def run_validation(self) -> TestCaseResult:
        """Run validation for the given test case

        Returns:
            TestCaseResult:
        """
        first_column_name = self._get_first_column_name()
        second_column_name = self._get_second_column_name()

        try:
            first_column_name = cast(str, first_column_name)
            second_column_name = cast(str, second_column_name)
            logger.warning(
                f'self._run_results({first_column_name}, {second_column_name})')
            res = self._run_results(first_column_name, second_column_name)
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
            f"Number of Rows code R29 = {res}.",
            [TestResultValue(name=ROW_COUNT, value=str(res))],
        )

    @abstractmethod
    def _get_first_column_name(self):
        raise NotImplementedError

    @abstractmethod
    def _get_second_column_name(self):
        raise NotImplementedError

    @abstractmethod
    def _run_results(self, date_column_name: str, second_column_name: str):
        raise NotImplementedError
