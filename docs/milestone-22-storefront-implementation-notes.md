# Milestone 22 storefront implementation notes

## V4 Flyway and JPA index hybrid boundary

Milestone 22 adds `idx_products_store_status_price_id` on `(store_id, status, price, id)` for storefront price sorting. The index is deliberately declared in two places because this project supports two schema-creation paths:

- `V4__add_storefront_price_index.sql` is the upgrade path for an existing PostgreSQL database. It first checks that `products` and the required `store_id`, `status`, and `price` columns exist, then uses `CREATE INDEX IF NOT EXISTS`. This keeps the migration safe for the repository's legacy and partially initialized migration test fixtures.
- `Product` mirrors the index in `@Table(indexes = ...)`. With `spring.jpa.hibernate.ddl-auto=update`, Hibernate may create `products` before Flyway reaches V4 on a fresh database; the JPA declaration ensures the same index exists on that path. V4 then becomes idempotent.

The matching name and column order are part of the contract. Changing one declaration without the other would make fresh and upgraded schemas diverge. Migration tests cover legacy migration, Spring Boot Flyway startup, and fresh PostgreSQL startup.

This is a bounded hybrid, not a general policy that Hibernate owns production migrations. Flyway remains the durable upgrade history; the JPA declaration only mirrors this index because fresh startup currently uses Hibernate schema update as well.

## Query budgets

- Public storefront header plus the first 12-product page: at most 3 prepared statements, with zero collection fetches.
- Operable-store list plus selected-store summary plus the first 12-product operator page: at most 6 prepared statements, with zero collection fetches.

The projections select a representative/fallback image and viewer wishlist/cart flags without loading to-many collections per row.

## Compatibility boundary

Buyer product summaries retain temporary `sellerId` and `sellerNickname` fields derived from the store's immutable owner. Historical order seller fields remain sourced from `Order.seller`. M22 does not rewrite historical seller rules or expose legal business data, administrator review data, memberships, or operator identity on buyer storefronts.

## Deferred scope

Manager invitation/assignment/reactivation, owner transfer, inventory policy, promotions, bulk import, and broad substring-search indexing remain outside M22. M24 owns the broader title-search strategy; no trigram index was added here.

## Verified UI boundary

The in-app browser walkthrough used `http://localhost:5173`. Anonymous active and suspended storefronts, public pagination/filter/sort, owner tabs/store switching/commands, manager catalog-only access and commands, inactive read-only controls, product-form store preselection, and 390x844 responsive behavior were exercised. Buyer pages exposed no private operator surface, operator mobile rows retained status/price/actions without horizontal overflow, and the browser warning/error log remained empty.
