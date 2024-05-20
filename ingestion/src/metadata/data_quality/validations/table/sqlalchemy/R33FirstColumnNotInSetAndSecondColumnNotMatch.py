from metadata.profiler.metrics.registry import Metrics
from metadata.utils.logger import test_suite_logger
from sqlalchemy import not_, and_

from metadata.data_quality.validations.table.base.BaseR33FirstColumnNotInSetAndSecondColumnNotMatch import (
    BaseR33FirstColumnNotInSetAndSecondColumnNotMatchValidator,
)

logger = test_suite_logger()


class R33FirstColumnNotInSetAndSecondColumnNotMatchValidator(
    BaseR33FirstColumnNotInSetAndSecondColumnNotMatchValidator
):
    """Validator for column values to be not null test case"""

    def _get_first_column_name(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "firstColumn",
            str,
        )

    def _get_second_column_name(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "secondColumn",
            str,
        )

    def _get_test_first_column_values(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "testFirstColumnValues",
            str,
        )

    def _get_test_second_column_regex(self):
        """returns the column name to be validated"""
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "testSecondColumnRegex",
            str,
        )

    def _run_results(self, first_column_name: str, second_column_name: str,
                     test_first_column_values: str, test_second_column_regex: str):
        """Execute the validation for the given test case

        Args:
            first_column_name (str): column name
            second_column_name (str): column name
            test_first_column_values (str): range interval
            test_second_column_regex (str): range interval
        """

        first_column_test = test_first_column_values.split('|')
        first_column = getattr(self.runner.table, first_column_name)
        second_column = getattr(self.runner.table, second_column_name)

        query = self.runner._build_query(Metrics.ROW_COUNT.value().fn()).select_from(self.runner.table)

        filter_ = and_(
            first_column.not_in(first_column_test),
            not_(second_column.regexp_match(test_second_column_regex))
        )

        res = dict(query.filter(filter_).first())

        return res.get(Metrics.ROW_COUNT.name)
