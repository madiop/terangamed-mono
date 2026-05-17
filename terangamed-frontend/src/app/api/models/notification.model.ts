// Modèles TS — miroir notification-service.

export type NotificationStatus = 'RECEIVED' | 'SENT_EMAIL' | 'SENT_SMS' | 'FAILED';

export interface NotificationDto {
  readonly id: number;
  readonly eventId: string; // UUID
  readonly sourceTopic: string;
  readonly eventType: string;
  readonly aggregateType: string;
  readonly aggregateId: string;
  readonly payloadJson?: string | null;
  readonly status: NotificationStatus;
  readonly receivedAt: string;
  readonly deliveredAt?: string | null;
  readonly deliveryError?: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly version: number;
}

export interface NotificationSearchCriteria {
  topic?: string;
  eventType?: string;
  aggregateType?: string;
  aggregateId?: string;
  status?: NotificationStatus;
  fromDate?: string;
  toDate?: string;
}
