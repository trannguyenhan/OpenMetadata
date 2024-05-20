from metadata.profiler.metrics.registry import Metrics
from metadata.utils.logger import test_suite_logger
from sqlalchemy import Column

from metadata.data_quality.validations.table.base.BaseR29FirstColumnLessThanSecondColumnAndFirstColumnGreaterThan01011900 import (
    BaseR29FirstColumnLessThanSecondColumnAndFirstColumnGreaterThan01011900Validator,
)

logger = test_suite_logger()


class R29FirstColumnLessThanSecondColumnAndFirstColumnGreaterThan01011900Validator(
    BaseR29FirstColumnLessThanSecondColumnAndFirstColumnGreaterThan01011900Validator):
    """Validator for column values to be not null test case"""

    def _get_first_column_name(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "firstColumn",
            Column,
        )

    def _get_second_column_name(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "secondColumn",
            Column,
        )

    def _run_results(self, first_column_name: str, second_column_name: str):
        """Execute the validation for the given test case

        Args:
            first_column_name (str): column name
            second_column_name (str): column name
        """

        return dict(
            self.runner.dispatch_query_select_first(
                Metrics.ROW_COUNT.value().fn(),
                query_filter_={
                    "filters": [
                        (first_column_name, "ge", second_column_name),
                        (first_column_name, "le", '1900-01-01'),
                    ],
                    "or_filter": True,
                },
            )  # type: ignore
        ).get(Metrics.ROW_COUNT.name)
