# Mechanics validation checklist

## Damage and stats

- Confirm scaling stat, multiplier, flat additions, bonus category, defense, resistance, critical behavior, snapshots, and damage attribution.
- Check live versus snapshotted stats and buff expiration at the exact event time.

## Reactions, aura, and ICD

- Check eligibility, gauge application, current-time decay, consumption, reaction tax simplification, ICD group, hit/time counters, ownership, and reaction result kind.
- Cover zero gauge, exact expiry, simultaneous events, shared ICD, no-ICD, and snapshot restore.

## Buffs and energy

- Check source/recipient, stacking key, cap, refresh policy, activation/expiry ordering, off-field behavior, particle ownership, flat energy, ER analysis, and report timeline.

## Content hooks

- Check normal trigger, invalid action, cooldown boundary, constellation/refinement boundary, weapon/artifact interaction, and party-wide effects.
- Keep config CSV and Java construction aligned.

## Cross-cutting consumers

- Optimizer: totals, ER convergence, determinism.
- RL: observation, privileged state, reward, action mask, snapshot branches.
- Reporting: reaction labels, direct versus transformative damage, aura, energy, buff uptime, formula detail.
