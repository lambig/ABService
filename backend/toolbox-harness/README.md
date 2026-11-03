# toolbox-harness: メソッドへのアクセス境界を検査する

利用者が選んだメソッドへの直接呼出しとメソッド参照を、許可したクラス内に限定するArchUnit規則です。
利用側の注釈・package・業務モデルを引数と検査対象にし、ライブラリ自体はサービス固有の型を持ちません。
構造上の違反を開発時に検出するためのもので、業務上の状態遷移や実行順序の正しさは利用側の試験が担います。

## 動く例から始める

[example](example/)は、独自のpackageと注釈を持つ独立したGradleプロジェクトです。
検査コードは[BoundaryCheck.java](example/src/test/java/example/shipping/BoundaryCheck.java)、
対象は[Shipment.java](example/src/main/java/example/shipping/Shipment.java)にあります。
説明用コードをREADMEに複製せず、そのソースを編集・実行します。

ビルドにはJDK 17以上が必要です。固定wrapperはGradle 9.7.1です。
ライブラリと例のbytecodeはJava 8を対象に生成します。検査の実行JVMとビルドJVMは別です。
依存するArchUnitはABServiceと同じ1.5.0に固定しています。

リポジトリのルートで、次を実行します。

```sh
sh backend/toolbox-harness/verify-example.sh
```

このコマンドはライブラリの単体試験、JAR・POM・sources・Javadocの生成を行い、
例を一時ディレクトリへコピーしてcandidate用Mavenリポジトリから取得します。
ライブラリのソースディレクトリやGradle compositeによる置換は、独立した例では使いません。
最後に次の結果が表示されれば、この導入経路を再現できています。

```text
H1: allowed PASS; direct REJECTED; reference REJECTED; fixed PASS; runtime isolated
```

失敗例はビルドが非ゼロ終了することに加え、対象メソッド・呼出元・規則の診断があることを確認します。
依存解決やコンパイルの失敗を、違反検出の成功とは扱いません。
各段階のログは `build/consumer-results/` に残ります。
別JVMでの検査には、JDKのディレクトリを指定します。

```sh
BOUNDARY_JAVA_HOME=/path/to/jdk8 sh backend/toolbox-harness/verify-example.sh
```

## 違反を見て修正する

自分で各段階を実行する場合、リポジトリのルートでcandidateを作り、空のディレクトリへ例をコピーします。

```sh
sh backend/gradlew -p backend/toolbox-harness publishMavenJavaPublicationToCandidateRepository
repository="file:$(pwd)/backend/toolbox-harness/build/repository"
consumer=$(mktemp -d)
cp -R backend/toolbox-harness/example/. "$consumer/"
cd "$consumer"
sh gradlew "-PharnessRepository=$repository" -Pscenario=allowed clean check
sh gradlew "-PharnessRepository=$repository" -Pscenario=direct clean check
sh gradlew "-PharnessRepository=$repository" -Pscenario=reference clean check
sh gradlew "-PharnessRepository=$repository" -Pscenario=fixed clean check
```

Windowsでは同じGradle引数を `gradlew.bat` に渡せます。
`harnessRepository` は生成したリポジトリの絶対file URLを指定してください。

| 段階 | 対象ソース | 結果と意味 |
| --- | --- | --- |
| allowed | [操作内の呼出しと参照](example/src/scenarios/allowed/java/example/shipping/DispatchOperation.java) | 両方とも許可され、成功 |
| direct | [操作外の直接呼出し](example/src/scenarios/direct/java/example/shipping/Client.java) | ClientからShipment.dispatchへの呼出しを検出し、失敗 |
| reference | [操作外のメソッド参照](example/src/scenarios/reference/java/example/shipping/Client.java) | Clientの参照取得を検出し、失敗 |
| fixed | [修正したClient](example/src/scenarios/fixed/java/example/shipping/Client.java)と[操作](example/src/scenarios/fixed/java/example/shipping/DispatchOperation.java) | 呼出しと参照取得を操作へ集め、同じ規則で成功 |

directとreferenceは意図的に失敗します。診断には呼出元、対象メソッド、ソース位置、
`expected caller class` に続く許可条件が表示されます。
修正例は検査を無効化せず、Clientから操作を経由するように変えます。
実際の業務では、その操作が必要な事前条件を満たすことも別途検証してください。

## 自分のプロジェクトに適用する

1. `example/build.gradle` の `testImplementation` とMavenリポジトリ設定を利用します。
   座標は `io.github.lambig:toolbox-harness:0.1.0-SNAPSHOT` です。
   この版はソースから作るcandidateで、Maven Central公開済みの版ではありません。
2. 自分の制限対象と許可元を表す注釈を用意します。例の
   [Transition](example/src/main/java/example/shipping/Transition.java)と
   [Operation](example/src/main/java/example/shipping/Operation.java)は利用側に属します。
3. BoundaryCheckの注釈型と `importPackages` を自分の対象に変更します。
   制限対象と呼出元の両方を、テスト対象のbytecodeとしてimportしてください。
4. `check` をCIから実行します。例はJUnit 4からruleの `check(JavaClasses)` を呼びます。
   別のテスト基盤でも同じAPIを呼べ、ライブラリ本体はJUnitに依存しません。

通常のライブラリ利用はtoolboxを総合入口とする方針ですが、Harnessはtest用依存として分離します。
このcandidateはtoolbox-javaの公開依存にはまだ接続されていません。
例の `verifyRuntimeIsolation` はアプリケーションのruntime依存が空であることを確認します。

注釈以外の選択が必要なら `MethodAccessRules.onlyAccessedBy(targets, allowedCallers)` に
ArchUnitの `DescribedPredicate` を渡せます。API契約とnull/空選択の扱いは
[MethodAccessRules](src/main/java/io/github/lambig/toolbox/harness/MethodAccessRules.java)のJavadocが正本です。
生成したAPI文書は `build/docs/javadoc/index.html` から参照できます。

## 検出範囲と限界

- classファイル上の直接呼出しとメソッド参照を検査します。参照を実行せず取得しただけでも検出対象です。
- 許可するのはアクセス元コードの所有クラスです。外側のクラスの注釈を内側のクラスへ自動継承しません。
  注釈方式は直接の注釈を照合し、meta-annotationでの許可には対応しません。
- 制限対象が一件もなければ失敗します。ただし一部のメソッドの注釈漏れや、
  importしなかった呼出元は検出できません。対象一覧・import範囲の妥当性は利用側が確認します。
- reflection、動的に生成したコード、実行時の業務順序、transaction、権限や値の妥当性は保証しません。
- メソッド参照を操作内で取得して外へ返すことは許可されます。
  「実行するスレッドや時刻も操作内に限定する」という保証にはなりません。
- この規則が成功しても、ライブラリの公開、全規則の外部導入、Skeleton全体の受入が完了した意味にはなりません。

## 更新と保守

依存版を変えるときは、実行例の座標と取得先を変更し、上の同じ一連操作を実行してください。
最低Java、ArchUnit版、公開メソッド、診断、空選択の扱いが利用契約への影響箇所です。
候補の間は同じSNAPSHOTを再生成できますが、公開した固定版は上書きせず新しい版にします。
公開版への切替時には、開発checkoutとローカルcandidateを外した状態で同じ操作を確認する必要があります。

ABServiceは `backend/settings.gradle` のcomposite buildで同じ候補をtest用に使用します。
ライブラリ単体のビルドにはQuarkus・DB・ABServiceの業務ソースは不要です。
ソースはこのリポジトリの独立buildとして保持し、公開先やtoolbox入口への接続は
[H1/S1の担当Issue](https://github.com/lambig/ABService/issues/248)で追跡します。
ArchUnit自体のimportと規則の基本は[公式ガイド](https://www.archunit.org/userguide/html/000_Index.html)を参照してください。
