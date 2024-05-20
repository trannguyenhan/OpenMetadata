from sqlalchemy import Column, text, func
from metadata.data_quality.validations.table.base.BaseR73FirstColumnGreaterThanSecondColumnAndFirstColumnLessThanXYearWithCurrentDay import (
    BaseR73FirstColumnGreaterThanSecondColumnAndFirstColumnLessThanXYearWithCurrentDayValidator
)
from metadata.profiler.metrics.registry import Metrics
from metadata.utils.logger import test_suite_logger
from metadata.utils.sqa_utils import (
    dispatch_to_date_or_datetime,
    get_partition_col_type,
)
logger = test_suite_logger()


class R73FirstColumnGreaterThanSecondColumnAndFirstColumnLessThanXYearWithCurrentDayValidator(
    BaseR73FirstColumnGreaterThanSecondColumnAndFirstColumnLessThanXYearWithCurrentDayValidator):
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

    def _get_num_of_year(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "numOfYear",
            int,
        )

    def _run_results(self, fist_column_name: str, second_column_name: str, years: int):
        """Execute the validation for the given test case

        Args:
            fist_column_name (str): column name
            second_column_name (str): column name
            years (int): range interval
        """
        date_or_datetime_fn = dispatch_to_date_or_datetime(
            years,
            text('YEAR'),
            get_partition_col_type(fist_column_name.name, self.runner.table.__table__.c),  # type: ignore
        )

        return dict(
            self.runner.dispatch_query_select_first(
                Metrics.ROW_COUNT.value().fn(),
                query_filter_={
                    "filters": [
                        (fist_column_name, "ge", second_column_name),
                        (date_or_datetime_fn, "le", fist_column_name),
                    ],
                    "or_filter": False,
                },
            )  # type: ignore
        ).get(Metrics.ROW_COUNT.name)
