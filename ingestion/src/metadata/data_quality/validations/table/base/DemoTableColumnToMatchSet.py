#  Copyright 2021 Collate
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#  http://www.apache.org/licenses/LICENSE-2.0
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

"""
Validator for table column to match set test case
"""

import collections
import traceback
from abc import abstractmethod

from metadata.data_quality.validations.base_test_handler import BaseTestValidator
from metadata.generated.schema.tests.basic import (
    TestCaseResult,
    TestCaseStatus,
    TestResultValue,
)
from metadata.utils.logger import test_suite_logger
from sqlalchemy.sql import ColumnCollection

logger = test_suite_logger()

COLUMN_NAMES = "columnNames"


class BaseDemoTableColumnToMatchSetValidator(BaseTestValidator):
    """Validator for table column to match set test case"""

    def compare(self, expected_names, actual_names) -> bool:
        expected_names_count = collections.Counter(expected_names)
        actual_names_count = collections.Counter(actual_names)
        logger.info(f"compare expected_names_count={expected_names_count},actual_names_count={actual_names_count}")
        return expected_names_count == actual_names_count

    def run_validation(self) -> TestCaseResult:
        """Run validation for the given test case

        Returns:
            TestCaseResult:
        """
        try:
            names: ColumnCollection = self._run_results()
        except Exception as exc:
            msg = f"Error computing {self.test_case.fullyQualifiedName}: {exc}"  # type: ignore
            logger.debug(traceback.format_exc())
            logger.warning(msg)
            return self.get_test_case_result_object(
                self.execution_date,
                TestCaseStatus.Aborted,
                msg,
                [TestResultValue(name=COLUMN_NAMES, value=None)],
            )

        expected_names = self.get_test_case_param_value(
            self.test_case.parameterValues, "columnNames", str  # type: ignore
        )

        expected_names = (
            [item.strip() for item in expected_names.split(",")]
            if expected_names
            else []
        )

        ordered = self.get_test_case_param_value(
            self.test_case.parameterValues,  # type: ignore
            "ordered",
            bool,
            default=False,
        )

        column_names = [column.name for column in names]

        logger.info(f'ordered={ordered}, expected_names={expected_names}, name={column_names}')
        if ordered:
            names_match = expected_names == column_names
        else:
            names_match = self.compare(expected_names, column_names)

        status = self.get_test_case_status(names_match)
        result_value = len(expected_names) if status == TestCaseStatus.Success else 0

        result = (
            f"Found {self.format_column_list(status, column_names)} column vs. "
            f"the expected column names {self.format_column_list(status, expected_names)}."
        )

        return self.get_test_case_result_object(
            self.execution_date,
            status,
            result,
            [TestResultValue(name=COLUMN_NAMES, value=str(result_value))],
        )

    @abstractmethod
    def _run_results(self):
        raise NotImplementedError
