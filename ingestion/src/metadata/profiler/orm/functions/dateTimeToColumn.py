from sqlalchemy.ext.compiler import compiles
from sqlalchemy.sql import func
from sqlalchemy.sql.functions import FunctionElement

from metadata.profiler.metrics.core import CACHE
from metadata.profiler.orm.registry import Dialects
from metadata.utils.logger import profiler_logger

logger = profiler_logger()


# --------------
# Date Functions
# --------------
class DateAddToColumnFn(FunctionElement):
    inherit_cache = CACHE


@compiles(DateAddToColumnFn)
def _(elements, compiler, **kwargs):
    """generic date and datetime function"""
    column = elements.clauses.clauses[0].value
    interval = elements.clauses.clauses[1].value
    interval_unit = compiler.process(elements.clauses.clauses[2], **kwargs)
    return f"CAST({column} - interval '{interval}' {interval_unit}  AS DATE)"


@compiles(DateAddToColumnFn, Dialects.Oracle)
def _(elements, compiler, **kwargs):
    """generic date and datetime function"""
    column = elements.clauses.clauses[0].value
    interval = elements.clauses.clauses[1].value
    interval_unit = compiler.process(elements.clauses.clauses[2], **kwargs)
    return f"TO_DATE({column} - INTERVAL '{interval}' {interval_unit})"


@compiles(DateAddToColumnFn, Dialects.BigQuery)
def _(elements, compiler, **kwargs):
    """generic date and datetime function"""
    column = elements.clauses.clauses[0].value
    interval = elements.clauses.clauses[1].value
    interval_unit = compiler.process(elements.clauses.clauses[2], **kwargs)
    return f"CAST({column} - interval {interval} {interval_unit} AS DATE)"


@compiles(DateAddToColumnFn, Dialects.MSSQL)
@compiles(DateAddToColumnFn, Dialects.AzureSQL)
@compiles(DateAddToColumnFn, Dialects.Snowflake)
def _(elements, compiler, **kwargs):
    """data function for mssql and azuresql"""
    column = elements.clauses.clauses[0].value
    interval = elements.clauses.clauses[1].value
    interval_unit = compiler.process(elements.clauses.clauses[2], **kwargs)
    return f"CAST(DATEADD({interval_unit},-{interval},{column}) AS DATE)"


@compiles(DateAddToColumnFn, Dialects.Db2)
@compiles(DateAddToColumnFn, Dialects.IbmDbSa)
def _(elements, compiler, **kwargs):
    """data function for DB2"""
    column = elements.clauses.clauses[0].value
    interval = elements.clauses.clauses[1].value
    interval_unit = compiler.process(elements.clauses.clauses[2], **kwargs)
    return f"CAST({column} - {interval} {interval_unit} AS DATE)"


@compiles(DateAddToColumnFn, Dialects.ClickHouse)
def _(elements, compiler, **kwargs):
    column = elements.clauses.clauses[0].value
    interval = elements.clauses.clauses[1].value
    interval_unit = compiler.process(elements.clauses.clauses[2], **kwargs)
    return f"toDate({column} - interval '{interval}' {interval_unit})"


@compiles(DateAddToColumnFn, Dialects.Redshift)
def _(elements, compiler, **kwargs):
    column = elements.clauses.clauses[0].value
    interval = elements.clauses.clauses[1].value
    interval_unit = compiler.process(elements.clauses.clauses[2], **kwargs)
    return f"DATEADD({interval_unit}, -{interval}, {column})"


@compiles(DateAddToColumnFn, Dialects.SQLite)
def _(elements, compiler, **kwargs):  # pylint: disable=unused-argument
    column = elements.clauses.clauses[0].value
    interval = elements.clauses.clauses[1].value
    interval_unit = compiler.process(elements.clauses.clauses[2], **kwargs)
    return f"DATE({column}, '-{interval} {interval_unit}')"