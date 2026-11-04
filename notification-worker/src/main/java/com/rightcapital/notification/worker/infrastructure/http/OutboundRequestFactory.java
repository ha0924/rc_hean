package com.rightcapital.notification.worker.infrastructure.http;

import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;

interface OutboundRequestFactory {

    OutboundHttpRequest create(DeliveryTaskDetail task);
}
