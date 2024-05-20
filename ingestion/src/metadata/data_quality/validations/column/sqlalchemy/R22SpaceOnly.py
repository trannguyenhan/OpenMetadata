from typing import Optional

from sqlalchemy import Column, inspect

from metadata.data_quality.validations.column.base.BaseR22SpaceOnly import (
    BaseR22SpaceOnlyValidator,
)
from metadata.data_quality.validations.mixins.sqa_validator_mixin import (
    SQAValidatorMixin,
)
from metadata.profiler.metrics.registry import Metrics
from metadata.utils.logger import test_suite_logger

logger = test_suite_logger()


class R22SpaceOnlyValidator(
    BaseR22SpaceOnlyValidator, SQAValidatorMixin
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

    def _run_results(self, metric: Metrics, column: Column) -> Optional[int]:
        """compute result of the test case

        Args:
            metric: metric
            column: column
        """
        return self.run_query_results(self.runner, metric, column)
