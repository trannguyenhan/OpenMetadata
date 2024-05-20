from typing import Optional

from sqlalchemy import Column, inspect, case, func, text

from metadata.data_quality.validations.column.base.BaseR15ValuecolumninCustomsubSQLQuery import (
    BaseR15ValuecolumninCustomsubSQLQueryValidator,
)
from metadata.data_quality.validations.mixins.sqa_validator_mixin import (
    SQAValidatorMixin,
)
from metadata.utils.logger import test_suite_logger

logger = test_suite_logger()


class R15ValuecolumninCustomsubSQLQueryValidator(
    BaseR15ValuecolumninCustomsubSQLQueryValidator, SQAValidatorMixin
):
    """Validator for column values to be not null test case"""

    def _get_column_name(self) -> Column:
        """Get column name from the test case entity link

        Returns:
            Column: column
        """
        return self.get_column_name(
            self.test_case.entityLink.__root__,
            inspect(self.runner.table).c,
        )

    def _get_sql_expression(self) -> str:
        """Get column name from the test case entity link

        Returns:
            Column: column
        """
        return self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "sqlExpression",
            str,
        )

    def _run_results(self, column: Column, sql_expression: str) -> Optional[int]:
        """compute result of the test case

        Args:
            column: column
            sql_expression: str
        """
        logger.warning(f'column={column}')

        query = (
            self.runner._session
            .query(func.sum(case([(column.in_(text(f'({sql_expression})')), 1)], else_=0)).label('rowCount'))
        )
        result = query.scalar()

        logger.warning(f'result={result}')

        return result
