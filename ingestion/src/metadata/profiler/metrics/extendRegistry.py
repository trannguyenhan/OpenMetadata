from metadata.profiler.registry import MetricRegistry

from metadata.profiler.metrics.static.blank_count import BlankCount
from metadata.profiler.metrics.static.count_not_in_set import countNotInSet
from metadata.profiler.metrics.static.not_regex_count_error import NotRegexCountError
from metadata.profiler.metrics.static.unique_count_nocase import UniqueCountNoCase
from metadata.profiler.metrics.static.none_word_character_count import NoneWordCharacterCount


class ExtendMetrics(MetricRegistry):
    # Static Metrics
    BLANK = BlankCount
    COUNT_NOT_IN_SET = countNotInSet
    NOT_REGEX_COUNT = NotRegexCountError
    UNIQUE_COUNT_NO_CASE = UniqueCountNoCase
    NONE_WORD_CHARACTER_COUNT = NoneWordCharacterCount
