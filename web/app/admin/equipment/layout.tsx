import { getPendingEquipment } from '@/lib/gym-api';
import { EquipmentSubTabs } from '@/components/admin/EquipmentSubTabs';

export const dynamic = 'force-dynamic';

export default async function EquipmentLayout({ children }: { children: React.ReactNode }) {
  const pending = await getPendingEquipment().catch(() => []);
  return (
    <div>
      <div className="border-b border-border-default bg-surface">
        <div className="container mx-auto max-w-7xl px-4">
          <EquipmentSubTabs pendingCount={pending.length} />
        </div>
      </div>
      {children}
    </div>
  );
}
