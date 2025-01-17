/*
 * Copyright 2022 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.observability;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.ExperimentalApi;
import io.grpc.ManagedChannelProvider.ProviderNotFoundException;
import io.grpc.observability.interceptors.InternalLoggingChannelInterceptor;
import io.grpc.observability.interceptors.InternalLoggingServerInterceptor;
import io.grpc.observability.logging.GcpLogSink;
import io.grpc.observability.logging.Sink;
import java.io.IOException;

/** The main class for gRPC Observability features. */
@ExperimentalApi("https://github.com/grpc/grpc-java/issues/8869")
public final class Observability {
  private static Observability instance = null;
  private final Sink sink;

  /**
   * Initialize grpc-observability.
   *
   * @throws ProviderNotFoundException if no underlying channel/server provider is available.
   */
  public static synchronized Observability grpcInit() throws IOException {
    if (instance == null) {
      GlobalLoggingTags globalLoggingTags = new GlobalLoggingTags();
      ObservabilityConfigImpl observabilityConfig = ObservabilityConfigImpl.getInstance();
      Sink sink = new GcpLogSink(observabilityConfig.getDestinationProjectId());
      instance = grpcInit(sink,
          new InternalLoggingChannelInterceptor.FactoryImpl(sink,
              globalLoggingTags.getLocationTags(), globalLoggingTags.getCustomTags(),
              observabilityConfig),
          new InternalLoggingServerInterceptor.FactoryImpl(sink,
              globalLoggingTags.getLocationTags(), globalLoggingTags.getCustomTags(),
              observabilityConfig));
    }
    return instance;
  }

  @VisibleForTesting static Observability grpcInit(Sink sink,
      InternalLoggingChannelInterceptor.Factory channelInterceptorFactory,
      InternalLoggingServerInterceptor.Factory serverInterceptorFactory) {
    if (instance == null) {
      instance = new Observability(sink, channelInterceptorFactory, serverInterceptorFactory);
    }
    return instance;
  }

  /** Un-initialize/shutdown grpc-observability. */
  public void grpcShutdown() {
    synchronized (Observability.class) {
      if (instance == null) {
        throw new IllegalStateException("Observability already shutdown!");
      }
      LoggingChannelProvider.shutdown();
      LoggingServerProvider.shutdown();
      sink.close();
      instance = null;
    }
  }

  private Observability(Sink sink,
      InternalLoggingChannelInterceptor.Factory channelInterceptorFactory,
      InternalLoggingServerInterceptor.Factory serverInterceptorFactory) {
    this.sink = checkNotNull(sink);
    LoggingChannelProvider.init(checkNotNull(channelInterceptorFactory));
    LoggingServerProvider.init(checkNotNull(serverInterceptorFactory));
  }
}
