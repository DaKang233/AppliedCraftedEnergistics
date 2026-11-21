---
navigation:
  title: Applied Crafted Energistics
  position: 200
---

# Applied Crafted Energistics

<Row>
  <ItemImage id="gas_cell_1k" scale="3" />
  <ItemImage id="gas_cell_64k" scale="3" />
  <BlockImage id="me_gas_interface" scale="4" />
  <ItemImage id="gas_p2p_tunnel" scale="3" />
</Row>

**Applied Crafted Energistics** bridges **Applied Energistics 2** and **Create: Crafted Beginning**. It makes
CCB gases a first-class ME resource: they can be stored in ME cells, moved by buses and pattern
providers, and teleported across the network with a P2P tunnel.

- **Gas Storage Cells** (1k / 4k / 16k / 64k / 256k / 1M / 4M / 16M / 64M / 256M) — store gas in the ME network.
  The 1M-256M tiers require **MEGA Cells** and use its cell components in their recipes.
  **1 AE byte = 1,000 GU** (CCB defines 1 GU as one litre of gas at 100 kPa), and the terminal displays
  kGU, so 1 kGU is one cubic metre at reference pressure.
- **ME Gas Interface** — connects the ME network to an adjacent gas tank or gas pipe run through a real
  pressure buffer vessel.
- **Gas P2P Tunnel** — moves gas between linked faces through the ME network.

## See Also

- [ME Gas Interface and Automation](gas_interface.md)
- [Gas Storage Cells](gas_cells.md)
- [Gas P2P Tunnel](gas_p2p_tunnel.md)
- [Cell Upgrade Cards](upgrades.md)

## Pressure in one sentence

The ME network only knows *how much* gas there is; Create: Crafted Beginning only knows *how much gas
fits in a vessel at a pressure*. Every crossing between the two goes through a real vessel rated to
CCB's safe pressure (16 atm), so an ME addon can never be the reason a player's airtight tank ruptures.
