# Block ads for the whole house

One setting on your Wi-Fi router blocks most ads and trackers on **every** device at home: the TV, phones,
tablets, laptops and game consoles. Nothing to install on each device, and it is free.

It works by changing the router's **DNS**, the "phone book" every device uses to find websites. An ad-blocking
DNS simply answers "not found" for ad and tracker servers, so the ads never load.

| What it blocks | What it can't block |
|---|---|
| Banner, pop-up and pop-under ads on websites | YouTube and Facebook video ads (they come from the same servers as the videos) |
| Ads inside most free apps and games | Ads drawn into a video or a live stream itself |
| Trackers and analytics in apps and smart TVs | Sponsored rows that an app gets from its own servers |
| Many known scam and malware sites | |

You can undo it at any time by setting the router's DNS back to **Automatic**.

## Option A: AdGuard DNS on the router (recommended, 5 minutes)

Nothing to buy. You only need the router's admin page.

### 1. Pick the servers

| Use | IPv4 (primary / secondary) | IPv6 (primary / secondary) |
|---|---|---|
| **Block ads and trackers** | `94.140.14.14` / `94.140.15.15` | `2a10:50c0::ad1:ff` / `2a10:50c0::ad2:ff` |
| **Family**: ads + adult sites, YouTube and Google Safe Search forced on | `94.140.14.15` / `94.140.15.16` | `2a10:50c0::bad1:ff` / `2a10:50c0::bad2:ff` |

Check the current addresses on [adguard-dns.io](https://adguard-dns.io/en/public-dns.html) before typing them
in; this table can go out of date.

### 2. Open the router's admin page

1. On a phone or laptop connected to the home Wi-Fi, open a browser and go to the router's address. It is
   printed on the sticker under the router, usually `192.168.0.1`, `192.168.1.1` or `192.168.100.1`.
2. Log in with the admin user name and password from the same sticker (not the Wi-Fi password, unless it
   says so). If it was changed and nobody remembers it, stop here and ask your internet provider; don't reset
   the router, or the whole house loses internet until it is set up again.

Many routers also have a phone app that can do this (TP-Link Tether or Deco, ASUS Router, Huawei AI Life,
Mercusys, Tenda WiFi).

### 3. Change the DNS

Where it is depends on the brand. Look for **DNS** in one of these places:

| Router | Where |
|---|---|
| TP-Link (Archer) | Advanced → Network → Internet → *Advanced* → DNS Address: **Use the following DNS addresses** |
| TP-Link Deco (app) | More → Advanced → IPv4 → DNS Server → Custom |
| ASUS | WAN → Internet Connection → WAN DNS Setting (or LAN → DHCP Server → DNS Server) |
| Huawei / Huawei AX (Unifi, Maxis) | More Functions → Network Settings → LAN → DHCP Server → Primary/Secondary DNS |
| ZTE (Unifi, Time) | Local Network → LAN → DHCP Server → DNS Server 1 / 2 |
| Mercusys / Tenda | Advanced → Network → Internet (or LAN / DHCP) → DNS |
| Mesh systems (Google Nest Wifi, eero) | the brand's app → Settings → Advanced / Network → DNS → Custom |

Type the two IPv4 addresses, and the IPv6 ones if there is an IPv6 DNS box, then **Save**. If both a
*WAN/Internet* DNS and a *LAN/DHCP* DNS setting exist, the LAN/DHCP one is the one devices receive; setting
both does no harm.

**ISP routers locked down?** Some Unifi, Maxis, Time and CelcomDigi routers hide or lock the DNS setting. Then
use Option C on each device, or put your own router behind the ISP one.

### 4. Let devices pick it up

Devices get the new DNS when they reconnect. Turn Wi-Fi off and on on each device, or restart the router once
(ask the family first; the internet drops for a minute or two).

### 5. Check

On any device at home, open [adguard.com/test](https://adguard.com/en/test.html) or
[d3ward.github.io/toolz/adblock](https://d3ward.github.io/toolz/adblock.html). Most of the tests should show as
blocked. On the TV, the stock home screen's ad rows usually turn empty.

## Option B: Pi-hole or AdGuard Home (for tinkerers)

Same idea, but the blocking runs on a small always-on computer in your home (a Raspberry Pi, an old laptop, or
a NAS). You get a dashboard showing what is blocked, per-device statistics, your own block and allow lists,
and a one-click switch to pause blocking.

- [Pi-hole](https://pi-hole.net) or [AdGuard Home](https://adguard.com/en/adguard-home/overview.html), both
  free and open source.
- After installing, give that computer a fixed IP address, and set the router's LAN/DHCP DNS to it (step 3
  above, with its IP instead of AdGuard's).
- If that computer is off or broken, nobody at home can open websites. Keep a second DNS that also blocks ads
  (for example `94.140.14.14`) as the router's secondary.

## Option C: per device (when the router can't be changed, or away from home)

| Device | Setting |
|---|---|
| **Android phone or tablet** (Android 9+) | Settings → Network & internet → **Private DNS** → *Private DNS provider hostname* → `dns.adguard-dns.com` (family: `family.adguard-dns.com`) |
| **Android TV / Google TV** | Not in the menus on most TVs. Glass TV's setup can set it over the network: `settings put global private_dns_mode hostname` then `settings put global private_dns_specifier dns.adguard-dns.com` (undo: `settings put global private_dns_mode opportunistic`) |
| **iPhone / iPad** | Install the free DNS profile from [adguard-dns.io](https://adguard-dns.io/en/public-dns.html), then Settings → General → VPN & Device Management → DNS → AdGuard DNS |
| **Windows 11** | Settings → Network & internet → Wi-Fi → your network → DNS server assignment → Edit → Manual → the IPv4 addresses above, DNS over HTTPS **On (automatic template)** |
| **Mac** | System Settings → Wi-Fi → Details → DNS → `+` → the IPv4 addresses above |

Private DNS on Android and the iPhone profile keep working on mobile data and other Wi-Fi networks too.

## If something stops working

Ad blocking occasionally breaks a site or app, most often shopping "deal" links, cashback apps, or ads you
*want* to click from a search result.

- Quick test: on that device, switch to mobile data. If it works there, the DNS blocked it.
- With AdGuard DNS on the router there is no allow list. Either set that one device's DNS back to automatic,
  or create a free account at [adguard-dns.io](https://adguard-dns.io) to get a personal DNS server with an
  allow list and statistics (put its addresses into the router instead).
- With Pi-hole or AdGuard Home, allow the blocked domain from the dashboard's query log.
- To turn it all off: set the router's DNS back to **Automatic** / *Get from ISP* and reconnect the devices.

## Some devices skip the router's DNS

- **Browsers** with "secure DNS" set to a specific provider (Chrome, Edge, Firefox, Brave) use that provider
  instead. In Chrome: Settings → Privacy and security → Security → *Use secure DNS* → **With your current
  service provider**.
- **IPv6**: if your provider gives out IPv6 and the router has no IPv6 DNS box, devices may still use the
  provider's IPv6 DNS. Fill in the IPv6 addresses above, or ask your provider.
- Some streaming sticks and apps hard-code Google DNS (`8.8.8.8`). Only a router that can redirect DNS
  traffic (or Pi-hole/AdGuard Home with such a rule) catches those.
