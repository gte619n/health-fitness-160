"use client";

import Link from 'next/link';
import type { Route } from 'next';
import { usePathname } from 'next/navigation';

type Tab = { href: Route; label: string; match: string };

const TABS: Tab[] = [
  { href: '/admin/equipment/catalog' as Route, label: 'Catalog', match: '/admin/equipment/catalog' },
  { href: '/admin/equipment/review' as Route, label: 'Review', match: '/admin/equipment/review' },
];

export function EquipmentSubTabs({ pendingCount }: { pendingCount: number }) {
  const pathname = usePathname();
  return (
    <nav className="-mb-px flex items-center gap-6 text-sm">
      {TABS.map((t) => {
        const active = pathname?.startsWith(t.match) ?? false;
        const label = t.label === 'Review' ? `Review (${pendingCount})` : t.label;
        return (
          <Link
            key={t.href}
            href={t.href}
            className={`border-b-2 py-3 transition-colors ${
              active
                ? 'border-accent text-primary'
                : 'border-transparent text-tertiary hover:text-secondary'
            }`}
          >
            {label}
          </Link>
        );
      })}
    </nav>
  );
}
