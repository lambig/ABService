package log

import (
    "strings"
    "testing"
)

func TestReplacementRuneSecurityRegression(t *testing.T) {
    cases := []struct { limit int; input, want string }{
        {5, strings.Repeat("\uFFFD", 1000), strings.Repeat("\uFFFD", 5)},
        {5, strings.Repeat("\uFFFD", 100) + "abcdef", strings.Repeat("\uFFFD", 5)},
        {2, "a\uFFFDbcd", "a\uFFFD"},
        {3, "ab\uFFFDcd", "ab\uFFFD"},
        {2, "a\uFFFD", "a\uFFFD"},
        {0, "\uFFFD", ""},
        {2, "a\x80\uFFFDcd", "a\uFFFD"},
        {3, "a\uFFFD\x80cd", "a\uFFFDc"},
    }
    for _, c := range cases {
        if got := truncate(c.limit, c.input); got != c.want {
            t.Fatalf("limit=%d got=%q want=%q", c.limit, got, c.want)
        }
    }
}
