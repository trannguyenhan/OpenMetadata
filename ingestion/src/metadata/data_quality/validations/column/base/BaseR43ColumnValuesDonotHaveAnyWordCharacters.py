import traceback
from abc import abstractmethod
from typing import Union

from sqlalchemy import Column

from metadata.data_quality.validations.base_test_handler import BaseTestValidator
from metadata.generated.schema.tests.basic import (
    TestCaseResult,
    TestCaseStatus,
    TestResultValue,
)
from metadata.utils.logger import test_suite_logger
from metadata.utils.sqa_like_column import SQALikeColumn

from metadata.profiler.metrics.extendRegistry import ExtendMetrics

logger = test_suite_logger()

COUNT = "Row Donot have any word character Count"


class BaseR43ColumnValuesDonotHaveAnyWordCharactersValidator(BaseTestValidator):
    """Validator for column values to be not null test case"""

    @abstractmethod
    def _run_results(self, column: Column, sql_expression: str):
        raise NotImplementedError

    def run_validation(self) -> TestCaseResult:
        """Run validation for the given test case

        Returns:
            TestCaseResult:
        """
        try:
            column: Union[SQALikeColumn, Column] = self._get_column_name()
            res = self._run_results(ExtendMetrics.NONE_WORD_CHARACTER_COUNT, column)
        except Exception as exc:
            msg = f"Error computing {self.test_case.fullyQualifiedName}: {exc}"  # type: ignore
            logger.debug(traceback.format_exc())
            logger.warning(msg)
            return self.get_test_case_result_object(
                self.execution_date,
                TestCaseStatus.Aborted,
                msg,
                [TestResultValue(name=COUNT, value=None)],
            )

        return self.get_test_case_result_object(
            self.execution_date,
            self.get_test_case_status(res == 0),
            f"Found blankCount={res}. It should be 0",
            [TestResultValue(name=COUNT, value=str(res))],
        )
