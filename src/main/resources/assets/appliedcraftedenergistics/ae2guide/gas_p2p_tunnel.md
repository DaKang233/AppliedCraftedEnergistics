---
navigation:
  parent: index.md
  title: Gas P2P Tunnel
  icon: appliedcraftedenergistics:gas_p2p_tunnel
  position: 30
item_ids:
  - appliedcraftedenergistics:gas_p2p_tunnel
---

# Gas P2P Tunnel

<Row>
  <ItemImage id="gas_p2p_tunnel" scale="4" />
</Row>

The **Gas P2P Tunnel** carries **Create: Crafted Beginning gas** between two faces through the ME
network, exactly like the other P2P tunnel types act as a wormhole between two block faces. It is
**not bidirectional**: one tunnel is the input, every linked tunnel is an output.

It is a *pipe* tunnel, not a *storage* tunnel — the gas is not visible to the ME network and does not
occupy cell space. If you want gas in the network, use an [ME Gas Interface](index.md) or an Import Bus.

## Getting one

- **Craft** it (see recipe below), or
- **Attune** any P2P tunnel: right-click it with a Create: Crafted Beginning **gas canister** (or any
  other item exposing CCB's gas-canister capability).

## Linking

P2P tunnels are linked with an AE2 **Memory Card**, the same way as every other tunnel type:

1. **Shift-right-click** one tunnel with the card — it gets a fresh frequency and becomes the **input**.
2. **Right-click** any number of other tunnels with the card — they join that frequency as **outputs**.

The frequency is drawn as a 2×2 colour grid on the back of the tunnel. Only tunnels of the same type
join the link.

## Mechanics: what the pipes actually see

This is the part that differs from item/fluid P2P. Create: Crafted Beginning does not have a
"teleport a stack" API: its pipe solver moves gas by comparing **pressures**, and it only transfers with
an endpoint that reports a **real vessel volume**. So each tunnel face presents a small pressure vessel:

| Property | Value |
|---|---|
| Buffer volume | 4,000 L (configurable, `gas_p2p.bufferVolumeLiters`) |
| Pressure rating | CCB's **safe** pressure, 16 atm (1,600 kPa) |
| Gas type | one at a time (the buffer is a single-tank vessel) |

- On the **input** side the buffer accepts gas from the adjacent gas line whenever that line's pressure
  is higher than the buffer's. This side refuses to be drained, so gas can only leave through the
  outputs.
- On an **output** side the same buffer is presented under the output part, so the adjacent gas line
  sees the input's gas at the input's pressure and drains it as if the tank were right there. Several
  outputs may drain the same buffer; each takes what its own pipe network can accept.
- Nothing is copied and nothing is stored twice: an output drains the input's buffer directly.
- Because the buffer is rated to 16 atm, the tunnel can never pressurise a player's tank past CCB's
  safe line, and it never bypasses CCB's overpressure rules — the pipe solver on both ends still does
  all pressure arithmetic.

The tunnel is therefore best thought of as **a remote piece of pipe with a small expansion tank**.
If the output side is blocked (valve closed, tank full) the buffer fills up to 16 atm and the input
simply stops accepting gas — no loss, no rupture. Gas that has been buffered is delivered as soon as
the output side can take it.

## Energy

A Gas P2P Tunnel uses 1 AE/t while it has a channel, and additionally charges AE for the gas actually
delivered to outputs (through AE2's normal P2P transport cost). Nothing is charged for gas sitting in
the buffer.

## Tips

- Place the input tunnel where the gas *enters*, and outputs where gas should *leave* — reversing the
  memory card paste reverses the flow.
- The tunnel only moves gas while both ends have a channel and are powered; unpowered tunnels hold
  their buffer contents.
- Prefer this over long gas pipe runs between distant machines; use a **ME Gas Interface** if you want
  the gas in the ME network rather than teleported.
- The output side can feed tanks, machines or pipe networks, including CCB's own pumps and valves.

## Recipe

<RecipeFor id="appliedcraftedenergistics:gas_p2p_tunnel" />
