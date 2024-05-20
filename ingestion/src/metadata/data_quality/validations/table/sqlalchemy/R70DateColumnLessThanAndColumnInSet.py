from sqlalchemy import Column, text
from metadata.data_quality.validations.table.base.BaseR70DateColumnLessThanAndColumnInSet import (
    BaseR70DateColumnLessThanAndColumnInSetValidator,
)
from metadata.profiler.metrics.registry import Metrics
from metadata.utils.logger import test_suite_logger
from metadata.utils.sqa_utils import (
    dispatch_to_date_or_datetime,
    get_partition_col_type,
)
logger = test_suite_logger()


class R70DateColumnLessThanAndColumnInSetValidator(BaseR70DateColumnLessThanAndColumnInSetValidator):
    """Validator for column values to be not null test case"""

    def _get_date_column_name(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "dateColumn",
            Column,
        )

    def _get_second_column_name(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "secondColumn",
            Column,
        )

    def _get_num_of_year(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "yearTestDateColumn",
            int,
        )

    def _get_values_test_column(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "secondColumnTestValues",
            str,
        )

    def _run_results(self, date_column_name: str, second_column_name: str, range_interval: int, values_test_column: str):
        """Execute the validation for the given test case

        Args:
            date_column_name (str): column name
            second_column_name (str): column name
            range_interval (int): range interval
            values_test_column (str): range interval
        """
        date_or_datetime_fn = dispatch_to_date_or_datetime(
            range_interval,
            text('YEAR'),
            get_partition_col_type(date_column_name.name, self.runner.table.__table__.c),  # type: ignore
        )

        values_tests = values_test_column.split('|')

        return dict(
            self.runner.dispatch_query_select_first(
                Metrics.ROW_COUNT.value().fn(),
                query_filter_={
                    "filters": [
                        (date_column_name, "ge", date_or_datetime_fn),
                        (second_column_name, "in", values_tests),
                    ],
                    "or_filter": False,
                },
            )  # type: ignore
        ).get(Metrics.ROW_COUNT.name)
