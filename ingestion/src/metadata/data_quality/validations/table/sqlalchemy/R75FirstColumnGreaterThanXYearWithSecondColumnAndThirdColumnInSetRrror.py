from sqlalchemy import Column, text, func
from metadata.data_quality.validations.table.base.BaseR75FirstColumnGreaterThanXYearWithSecondColumnAndThirdColumnInSetRrror import (
    BaseR75FirstColumnGreaterThanXYearWithSecondColumnAndThirdColumnInSetRrrorValidator,
)
from metadata.profiler.metrics.registry import Metrics
from metadata.utils.logger import test_suite_logger
from metadata.utils.sqa_utils import (
    dispatch_to_date_or_datetime,
    get_partition_col_type,
)

from metadata.profiler.orm.functions.dateTimeToColumn import DateAddToColumnFn

logger = test_suite_logger()


class R75FirstColumnGreaterThanXYearWithSecondColumnAndThirdColumnInSetRrrorValidator(
    BaseR75FirstColumnGreaterThanXYearWithSecondColumnAndThirdColumnInSetRrrorValidator):
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

    def _get_third_column_name(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "thirdColumn",
            Column,
        )

    def _get_num_of_year(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "numOfYear",
            int,
        )

    def _get_values_test_column(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "thirdColumnTestValues",
            str,
        )

    def _run_results(self, first_column_name: str, second_column_name: str, third_column_name: str,
                     range_interval: int, values_test_column: str):
        """Execute the validation for the given test case

        Args:
            first_column_name (str): column name
            second_column_name (str): column name
            third_column_name (str): column name
            range_interval (int): range interval
            values_test_column (str): range interval
        """
        values_tests = values_test_column.split('|')

        added_date_column = DateAddToColumnFn(second_column_name.name, range_interval, text('YEAR'))

        return dict(
            self.runner.dispatch_query_select_first(
                Metrics.ROW_COUNT.value().fn(),
                query_filter_={
                    "filters": [
                        (added_date_column, "le", first_column_name),
                        (third_column_name, "in", values_tests),
                    ],
                    "or_filter": False,
                },
            )  # type: ignore
        ).get(Metrics.ROW_COUNT.name)
