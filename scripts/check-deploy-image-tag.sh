#!/usr/bin/env bash
# 配布が発行するイメージタグと、ECR の保持規則が選ぶタグを突き合わせる。
#
# 保持規則（infra/ecr.tf）は接頭辞でタグを選ぶ。配布（.github/workflows/deploy.yml）が別の接頭辞で
# タグを発行すると、そのイメージは規則の対象外になり、「直近N件」の意図が効かないまま溜まり続ける。
# ずれても何も落ちず、ストレージの請求で気付くことになるため、実行の前にここで見る。
#
# 見るのは3点。配布が発行するタグの接頭辞が保持規則の接頭辞に含まれること、保持規則の接頭辞が
# 配布の発行しない接頭辞を持たないこと（数に入らない規則を残さない）、配布が移動するタグ（latest）を
# 発行しないこと（どの実体を指すか時点で変わるタグは、保持の数え方も証跡も曖昧にする）。
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflow=".github/workflows/deploy.yml"
policy="infra/ecr.tf"

# 配布は checkout した commit からタグを導く。`tag=<接頭辞>$(git rev-parse HEAD)` の形を正とする
issued="$(sed -nE 's/.*"tag=([a-z0-9-]+)\$\(git rev-parse HEAD\)".*/\1/p' "$root/$workflow" | sort -u)"

if [ "$(wc -l <<<"$issued")" -ne 1 ] || [ -z "$issued" ]; then
  echo "$workflow must derive exactly one image tag as tag=<prefix>\$(git rev-parse HEAD)." >&2
  echo "Found: ${issued:-nothing}" >&2
  exit 1
fi

# 保持規則の接頭辞。`tagPrefixList = ["a", "b"]` を1行で書く前提（複数行に割ると読めず、ここで落ちる）
retained="$(sed -nE 's/.*tagPrefixList *= *\[([^]]*)\].*/\1/p' "$root/$policy" | tr -d ' "' | tr ',' '\n' | sed '/^$/d' | sort -u)"

if [ -z "$retained" ]; then
  echo "$policy has no tagPrefixList; the retention rule for tagged images is missing or not on one line." >&2
  exit 1
fi

status=0

if ! grep -qxF "$issued" <<<"$retained"; then
  echo "$workflow issues tags with prefix '$issued', but $policy retains only: $(tr '\n' ' ' <<<"$retained")" >&2
  echo "Images the deploy pushes would fall outside the retention rule and accumulate." >&2
  status=1
fi

while read -r prefix; do
  if [ "$prefix" != "$issued" ]; then
    echo "$policy retains prefix '$prefix', but $workflow never issues it. Drop it so the rule matches what is pushed." >&2
    status=1
  fi
done <<<"$retained"

if grep -qE ':latest\b' "$root/$workflow"; then
  echo "$workflow pushes a moving tag (latest). Deploy by the commit tag and digest only." >&2
  status=1
fi

if [ "$status" -eq 0 ]; then
  echo "$workflow issues '$issued<sha>' and $policy retains the same prefix; no moving tag is pushed."
fi

exit "$status"
