from typing import Optional

from sqlalchemy import Column, inspect, case, func, text, not_

from metadata.data_quality.validations.column.base.BaseR20NumberCharCharactersInCustomsubSQLQuery import (
    BaseR20NumberCharCharactersInCustomsubSQLQueryValidator,
)
from metadata.data_quality.validations.mixins.sqa_validator_mixin import (
    SQAValidatorMixin,
)
from metadata.utils.logger import test_suite_logger

logger = test_suite_logger()


class R20NumberCharCharactersInCustomsubSQLQueryValidator(
    BaseR20NumberCharCharactersInCustomsubSQLQueryValidator, SQAValidatorMixin
):
    """Validator for column values to be not null test case"""

    def _get_column_name(self) -> Column:
        return self.get_column_name(
            self.test_case.entityLink.__root__,
            inspect(self.runner.table).c,
        )

    def _get_sql_expression(self) -> str:
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "sqlExpression",
            str,
        )

    def _get_length_of_value(self) -> int:
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "lengthOfValue",
            int,
        )

    def _run_results(self, column: Column, lengthOfValue: int, sql_expression: str) -> Optional[int]:
        """compute result of the test case

        Args:
            column: column
            lengthOfValue: int
            sql_expression: str
        """

        query = (
            self.runner._session
            .query(
                func.sum(case([(not_(func.left(column, lengthOfValue).in_(text(f'({sql_expression})'))), 1)], else_=0)).label('rowCount')
            )
        )
        result = query.scalar()

        logger.warning(f'result={result}')

        return result
