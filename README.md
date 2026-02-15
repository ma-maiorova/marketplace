# Marketplace — Architecture (C4 + init 1 service)

Бизнес-логика отсутствует.

## Требования
Маркетплейс должен поддерживать:
- персонализированную ленту товаров (популярное в категории + недавно просмотренные)
- каталог товаров (продавец у товара один)
- управление каталогом (для продавцов)
- управление пользователями (покупатели и продавцы)
- поиск по товарам
- корзину и оформление заказа
- учёт платежей
- уведомления о статусах заказов

## Архитектура
### Домены
- **Users** — профили покупателей и продавцов, роли, настройки уведомлений
- **Catalog** — товары (у товара один продавец), категории, атрибуты, «похожие товары»
- **Search** — индекс и поиск по каталогу
- **Feed** — персонализация: популярное в категории + недавно просмотренные; сбор событий просмотров
- **Orders/Cart** — корзина (черновик заказа), создание заказа, статусы заказа
- **Payments** — платежи, транзакции, статусы, интеграция с платёжным провайдером
- **Notifications** — отправка уведомлений по событиям статусов заказа и платежа

## Декомпозиция
### Вариант A (выбран): микросервисы по доменам + события
Сервисы: `user-service`, `catalog-service`, `feed-service`, `order-service`, `payment-service`, `notification-service`.
Интеграция:
- синхронно (REST) для пользовательских запросов
- асинхронно (broker) для событий статусов и уведомлений

**Плюсы:**
- Ясные границы ответственности и владения данными (отдельная БД для каждого сервиса).
- Легко масштабировать домены search отдельно от платежей.
- Уведомления и статусы делаются асинхронно.

**Минусы:**
- Больше инфраструктуры (broker, наблюдаемость).
- Сложнее локальная разработка: больше сервисов и контрактов.
- Eventual consistency между сервисами.

### Вариант B: модульный монолит
Один backend, внутри модули доменов.

**Плюсы:** проще деплой и дебаг, меньше сетевых вызовов.

**Минусы:** хуже видны границы владения данными, сложнее независимо масштабировать (только целиком, нельзя отдельно расширить одну часть).

### Вариант C: гибрид (commerce-service + критичные сервисы отдельно)
Например: `commerce-service` (users+catalog+feed), отдельно `order-service`, `payment-service`, `notification-service`.

**Плюсы:** критичные домены изолированы и могут отдельно развиваться, меньше сервисов.

**Минусы:** внутри границы доменов менее строгие, много доменов, слоэнее для независимого развития.

## 4) Обоснование финального выбора
Выбран **Вариант A**, потому что:
- В критериях ДЗ требуют отсутствие shared DB.
- Доменная декомпозиция лучше соответствует требованиям.
- Персонализация и поиск имеют иной профиль нагрузки.
- Асинхронные события естественно покрывают уведомления о статусах заказов.

## 5) Распределение доменов по сервисам
- **user-service**: Users
- **catalog-service**: Catalog (+ публикация изменений для Search)
- **feed-service**: Feed (использует Catalog read по API)
- **order-service**: Orders + Cart
- **payment-service**: Payments
- **notification-service**: Notifications

Логика разбиения: каждый сервис соответствует одному домену и владеет его данными; взаимодействие — через API или события.

## 6) Границы владения данными (DB per service)
| Сервис | Владение данными (system of record) | Отвечает за |
|---|---|---|
| user-service | users, roles, seller_profile, notification_prefs | учётные записи и роли |
| catalog-service | products, categories, attributes, sellerId | карточки товара и каталог |
| feed-service | view_events, category_popularity, feed_cache_keys | персонализация ленты |
| order-service | carts, orders, order_items, order_status_history | корзина и заказы |
| payment-service | payments, transactions, payment_status_history | платежи и учёт |
| notification-service | templates, delivery_logs, outbox (опц.) | отправка уведомлений |

**Shared DB отсутствует.**
- `Redis` — кэш 
- `Search index` — индекс

## 7) Взаимодействия сервисов (sync/async)
**Синхронно (REST):**
- UI → API Gateway/BFF → user/catalog/feed/order
- order-service → payment-service (инициация/проверка оплаты)
- catalog-service → search-index (поисковые запросы, а обновления индекса — через события/воркер)

**Асинхронно (broker):**
- order-service публикует `OrderStatusChanged`
- payment-service публикует `PaymentStatusChanged`
- notification-service подписывается на события и отправляет уведомления
- feed-service использует `ProductViewed` для «недавно просмотренных»

## 8) C4 Container diagram (LikeC4)
Файл диаграммы: `docs/architecture/marketplace.c4`

Просмотр локально:
```bash
npx likec4 start
```
Экспорт PNG (опционально):
```bash
npx likec4 export png -o ./docs/assets
```

## 9) Архитектурные решения (ADR)
- `docs/adr/ADR-001-domain-microservices.md`
- `docs/adr/ADR-002-async-events.md`
- `docs/adr/ADR-003-db-per-service.md`

## 10) Реализованный сервис (без бизнес-логики)
Поднят один сервис: **catalog-service** (Java + Spring Boot). Он содержит только endpoint:
- `GET /health` → `200 OK`

### Запуск (Docker Compose)
Из корня репозитория:
```bash
docker compose up --build
```
Проверка:
```bash
curl -i http://localhost:8080/health
```
Ожидается `HTTP/1.1 200 OK`.

### Запуск без compose (docker build/run)
```bash
cd services/catalog-service
docker build -t catalog-service:0.0.1 .
docker run --rm -p 8080:8080 catalog-service:0.0.1
```
