# OrdersPlus

**Join the SQWARE Discord: [discord.sqware.gg](https://discord.sqware.gg).**

OrdersPlus is a Minecraft market plugin for Paper servers. It adds escrow-backed buy orders so players can request items or blocks, pay up front, and let other players fulfill the order for money.

Use it alongside or instead of a normal auction house when your economy needs demand-side trading: buyers post what they want, sellers deliver it.

## Features

- Buy orders for materials and block items.
- Vault-backed escrow: the buyer pays up front.
- Sellers fulfill orders by providing the requested items.
- Partial fulfillment support.
- Buyer claim queue for fulfilled items.
- Order search and material lookup.
- Clickable in-game announcements when new orders are created.
- Active order limits with bypass permission.
- Plain-item validation by default to avoid unsafe custom item trades.
- API events for creation, fulfillment, cancellation, and expiry.
- DiscordPlus integration for order listings and order events.

## Requirements

- Paper `26.1.2+`
- Java `25+`
- Vault
- A Vault-compatible economy plugin
- Maven

## Commands

```text
/orders
/orders search <text>
/orders items
/orders create <material> <amount> <price-each> [duration]
/orders fulfill <id> [amount]
/orders claim [id|all] [amount]
/orders manage
/orders cancel <id>
/orders help

/ordersplus stats
/ordersplus reload
/ordersplus save
/ordersplus cancel <id>
```

Aliases: `/order`, `/market`, `/ordersadmin`

## Permissions

```text
ordersplus.use          - browse orders
ordersplus.create       - create buy orders
ordersplus.fulfill      - fulfill orders
ordersplus.announce.receive - receive order creation announcements when configured
ordersplus.cancel       - cancel own active orders
ordersplus.search       - search orders and materials
ordersplus.limit.bypass - bypass active order limits
ordersplus.admin        - admin commands
```

## Item Safety

By default, fulfillment only accepts plain stackable items with no custom item data. Server owners can loosen this in `config.yml` if their economy intentionally supports custom items.

## Announcements

New buy orders can be announced in chat so sellers notice them quickly. The default announcement is clickable and suggests `/orders fulfill <id>` to eligible recipients.

Configure this under `announcements.order-created` in `config.yml`, including recipient permission, buyer visibility, click action, command, format, and hover text.

## Build

```powershell
mvn package
```

The jar is written to `target/OrdersPlus-0.1.0.jar`.

## License

OrdersPlus is licensed under the Apache License, Version 2.0.
