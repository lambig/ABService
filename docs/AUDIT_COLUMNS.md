# 共通監査列ガイドライン

## 概要

ABServiceプロジェクトでは、すべてのデータベーステーブルに共通の監査列を含める必要があります。
これにより、データの作成・更新の履歴を追跡し、監査要件を満たすことができます。

## 必須監査列（7列）

すべてのテーブルには以下の7つの共通監査列を含める必要があります。

| 列名 | 型 | NULL許可 | デフォルト値 | 説明 |
|------|-----|----------|-------------|------|
| `created_at` | TIMESTAMP | NOT NULL | CURRENT_TIMESTAMP | レコード作成日時 |
| `updated_at` | TIMESTAMP | NOT NULL | CURRENT_TIMESTAMP | レコード最終更新日時 |
| `created_by_service` | VARCHAR(255) | NULL | - | 作成時のアプリケーションサービス名 |
| `updated_by_service` | VARCHAR(255) | NULL | - | 更新時のアプリケーションサービス名 |
| `created_by_user` | VARCHAR(255) | NULL | - | 作成者ユーザーID（外部サービスのユーザーID） |
| `updated_by_user` | VARCHAR(255) | NULL | - | 更新者ユーザーID（外部サービスのユーザーID） |
| `version` | INTEGER | NOT NULL | 0 | 楽観ロック用バージョン番号 |

## ルール

1. **新規テーブル作成時**: 上記7列をすべて含めること
2. **タイムスタンプの扱い**:
   - `created_at`/`updated_at`はDBのDEFAULT値に任せる（アプリケーションで明示的に設定しない）
   - 業務的にハンドルする必要があるタイムスタンプは別カラムに持つこと
3. **ユーザーID型**: `created_by_user`/`updated_by_user`はVARCHAR型（外部サービスのユーザーIDを想定）
4. **楽観ロック**: JPAの`@Version`アノテーションを使用

## JPA Entity実装例

### 基本的な使い方

すべてのエンティティは `AuditableEntity` を継承します。

```java
package com.abservice.infrastructure.persistence.entity;

import com.abservice.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "example")
public class ExampleEntity extends AuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}
```

### 監査情報の設定

エンティティの作成・更新時に監査情報を設定します。

```java
import com.abservice.infrastructure.persistence.AuditInfo;

// 新規作成時
ExampleEntity entity = new ExampleEntity();
entity.setName("Example");
entity.setCreationAuditInfo(new AuditInfo("ab-service", "user-123"));

// 更新時
entity.setName("Updated Example");
entity.setUpdateAuditInfo(new AuditInfo("ab-service", "user-456"));
```

### ファクトリメソッドの使用

サービス名のみ、またはユーザーIDのみを設定する場合はファクトリメソッドを使用できます。

```java
// サービス名のみを設定
entity.setCreationAuditInfo(AuditInfo.fromService("ab-service"));

// ユーザーIDのみを設定
entity.setUpdateAuditInfo(AuditInfo.fromUser("user-123"));
```

## Flywayマイグレーション例

### 新規テーブル作成

```sql
CREATE TABLE example (
    id BIGSERIAL PRIMARY KEY,
    -- 業務カラム
    name VARCHAR(255) NOT NULL,
    -- 共通監査列
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_service VARCHAR(255),
    updated_by_service VARCHAR(255),
    created_by_user VARCHAR(255),
    updated_by_user VARCHAR(255),
    version INTEGER NOT NULL DEFAULT 0
);

-- カラムコメント
COMMENT ON COLUMN example.id IS 'プライマリキー';
COMMENT ON COLUMN example.name IS '名前';
COMMENT ON COLUMN example.created_at IS 'レコード作成日時';
COMMENT ON COLUMN example.updated_at IS 'レコード最終更新日時';
COMMENT ON COLUMN example.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN example.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN example.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN example.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN example.version IS '楽観ロック用バージョン番号';

-- インデックス
CREATE INDEX idx_example_created_at ON example(created_at);
CREATE INDEX idx_example_updated_at ON example(updated_at);
```

### 既存テーブルへの監査列追加

既存のテーブルに監査列を追加する場合のマイグレーション例:

```sql
-- 監査列の追加
ALTER TABLE existing_table
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN created_by_service VARCHAR(255),
    ADD COLUMN updated_by_service VARCHAR(255),
    ADD COLUMN created_by_user VARCHAR(255),
    ADD COLUMN updated_by_user VARCHAR(255),
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

-- カラムコメントの追加
COMMENT ON COLUMN existing_table.created_at IS 'レコード作成日時';
COMMENT ON COLUMN existing_table.updated_at IS 'レコード最終更新日時';
COMMENT ON COLUMN existing_table.created_by_service IS '作成時のアプリケーションサービス名';
COMMENT ON COLUMN existing_table.updated_by_service IS '更新時のアプリケーションサービス名';
COMMENT ON COLUMN existing_table.created_by_user IS '作成者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN existing_table.updated_by_user IS '更新者ユーザーID（外部サービスのユーザーID）';
COMMENT ON COLUMN existing_table.version IS '楽観ロック用バージョン番号';

-- インデックスの追加
CREATE INDEX idx_existing_table_created_at ON existing_table(created_at);
CREATE INDEX idx_existing_table_updated_at ON existing_table(updated_at);
```

## 参考

この実装は [ABService](https://github.com/lambig/ABService) プロジェクトの共通監査列設計を参考にしています。

詳細は以下を参照してください:
- [ABService CODING_GUIDELINES.md](https://github.com/lambig/ABService/blob/main/docs/CODING_GUIDELINES.md)

## 関連クラス

- `com.abservice.infrastructure.persistence.AuditableEntity` - 共通監査列を持つエンティティの基底クラス
- `com.abservice.infrastructure.persistence.AuditInfo` - 監査情報を保持するデータクラス
