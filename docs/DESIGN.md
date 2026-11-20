# Design system

One set of tokens for both apps. iOS reads the brand color from the `AccentColor` asset; Android from `TamdidColors` in `ui/theme/Theme.kt`.

## Color

| Token | Light | Dark | Use |
|---|---|---|---|
| Brand | `#5B4BDB` | `#9D92FF` | Primary actions, selected states, the total card |
| Positive | `#12A594` | | Free-trial badge, "notifications on" |
| Warning | `#F76B15` | | Paused badge, ending-trial amounts |
| Danger | `#E5484D` | | Destructive actions |
| Background | `#F6F6FA` | `#111116` | Screens |
| Surface | `#FFFFFF` | `#0C0C10` | Cards and grouped lists |

Subscription swatches (also used, in order, for category charts):
`#5B4BDB #E5484D #12A594 #F76B15 #3E63DD #D6409F #30A46C #8E4EC6 #0090FF #AD7F58 #E54666 #46A758`

## Shape and spacing

- Corner radius: 10 (small controls), 16 (cards), 24 (the total card).
- Screen gutter 16; space between sections 20; list rows 12 vertical padding.
- Service avatars are rounded squares (radius 30% of size) with the service's initial. **No third-party logos.**

## Type

System fonts on both platforms (San Francisco, Roboto). The total uses a large rounded weight on iOS and `displaySmall` on Android. A bundled Persian/Arabic typeface (Vazirmatn) is planned for v2.

## Language and direction

- Five languages: English, Persian, Arabic, Turkish, German. Persian and Arabic are right-to-left; layouts mirror automatically and directional icons use auto-mirrored variants.
- Digits follow the language: Persian `۰۱۲`, Arabic-Indic `٠١٢`, Latin elsewhere.
- Number separators: `1,234.56` (en), `۱٬۲۳۴٫۵۶` (fa), `١٬٢٣٤٫٥٦` (ar), `1.234,56` (tr, de).
- Weeks start on Saturday for the Solar Hijri calendar and in Arabic, otherwise Monday.
- Charts label months by number for the Solar Hijri calendar and in Arabic script, where month names are long and share prefixes.
