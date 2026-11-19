---
navigation:
  title: 应用机巧能源
  position: 200
---

# 应用机巧能源

<Row>
  <ItemImage id="gas_cell_1k" scale="3" />
  <ItemImage id="gas_cell_64k" scale="3" />
  <BlockImage id="me_gas_interface" scale="4" />
  <ItemImage id="gas_p2p_tunnel" scale="3" />
</Row>

**应用机巧能源（Applied Crafted Energistics）** 是 **应用能源 2（AE2）** 与 **机械动力：机巧新生
（Create: Crafted Beginning）** 之间的桥梁：把 CCB 的气体变成 ME 网络的一等资源——可以装进气体
存储元件、被总线与样板供应器搬运，也可以经 P2P 隧道在网络的另一头出现。

- **气体存储元件**（1k / 4k / 16k / 64k / 256k）——把气体存进 ME 网络。
  **1 AE 字节 = 1,000 GU**（CCB 定义 1 GU 为 100 kPa 参考压下 1 升气体），终端以 kGU 显示，
  1 kGU 即 1 立方米参考压气体。
- **ME 气体接口**——通过一只真实的压力缓冲罐，把 ME 网络与相邻的气体储罐 / 气管网连通。
- **气体 P2P 隧道**——通过 ME 网络在已连接的两个面之间输送气体。

## 另见

- [气体 P2P 隧道](gas_p2p_tunnel.md)
- [元件升级卡](upgrades.md)

## 一句话说清气压

ME 网络只知道"有多少气"，Create: Crafted Beginning 只知道"这只罐在某个压力下能装多少气"。
两者之间每一次搬运都必须经过一只**真实容器**，且这只容器只按 CCB 的**安全压力（16 atm）**设计，
所以本模组永远不会成为把玩家密封储罐压爆的原因。
