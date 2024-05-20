import traceback
from typing import Union
from abc import abstractmethod
from sqlalchemy import Column
from typing import cast
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

IN_SET_COUNT = "In set count"


class BaseR15ValuecolumninCustomsubSQLQueryValidator(BaseTestValidator):
    """Validator for column values to be not null test case"""

    @abstractmethod
    def _get_column_name(self):
        raise NotImplementedError
    @abstractmethod
    def _get_sql_expression(self):
        raise NotImplementedError

    @abstractmethod
    def _run_results(self, column: Column, sql_expression: str):
        raise NotImplementedError

    def run_validation(self) -> TestCaseResult:
        """Run validation for the given test case

        Returns:
            TestCaseResult:
        """
        sql_expression = self._get_sql_expression()

        try:

            sql_expression = cast(str, sql_expression)
            column: Union[SQALikeColumn, Column] = self._get_column_name()
            res = self._run_results(column, sql_expression)
        except Exception as exc:
            msg = f"Error computing {self.test_case.fullyQualifiedName}: {exc}"  # type: ignore
            logger.debug(traceback.format_exc())
            logger.warning(msg)
            return self.get_test_case_result_object(
                self.execution_date,
                TestCaseStatus.Aborted,
                msg,
                [TestResultValue(name=IN_SET_COUNT, value=None)],
            )

        return self.get_test_case_result_object(
            self.execution_date,
            self.get_test_case_status(res == 0),
            f"Found row={res} in set",
            [TestResultValue(name=IN_SET_COUNT, value=str(res))],
        )
