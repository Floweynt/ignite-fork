/*
 * This file is part of Ignite, licensed under the MIT License (MIT).
 *
 * Copyright (c) vectrix.space <https://vectrix.space/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package space.vectrix.ignite.api.config;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.ScopedConfigurationNode;
import org.spongepowered.configurate.gson.GsonConfigurationLoader;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.loader.AbstractConfigurationLoader;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Provides a useful way of retrieving {@link Configuration}s for a specific
 * {@link ConfigurationLoader} and {@link Configuration.Key}.
 *
 * @since 0.5.0
 */
@SuppressWarnings("unchecked")
public final class Configurations {
  /**
   * Provides a function to make a general purpose {@link GsonConfigurationLoader}
   * using the input {@link Configuration.Key}.
   *
   * @since 0.5.0
   */
  public static final @NonNull Function<Configuration.Key<?>, ConfigurationLoader<BasicConfigurationNode>> GSON_LOADER = key -> Configurations.createLoader(key, path -> GsonConfigurationLoader.builder()
    .source(() -> Files.newBufferedReader(path, StandardCharsets.UTF_8))
    .sink(() -> Files.newBufferedWriter(path, StandardCharsets.UTF_8, Configurations.SINK_OPTIONS))
    .defaultOptions(ConfigurationOptions.defaults())
    .build()
  );

  /**
   * Provides a function to make a general purpose {@link HoconConfigurationLoader}
   * using the input {@link Configuration.Key}.
   *
   * @since 0.5.0
   */
  public static final @NonNull Function<Configuration.Key<?>, ConfigurationLoader<CommentedConfigurationNode>> HOCON_LOADER = key -> Configurations.createLoader(key, path -> HoconConfigurationLoader.builder()
    .source(() -> Files.newBufferedReader(path, StandardCharsets.UTF_8))
    .sink(() -> Files.newBufferedWriter(path, StandardCharsets.UTF_8, Configurations.SINK_OPTIONS))
    .defaultOptions(ConfigurationOptions.defaults())
    .build()
  );

  /**
   * Provides a function to make a general purpose {@link YamlConfigurationLoader}
   * using the input {@link Configuration.Key}.
   *
   * @since 0.5.0
   */
  public static final @NonNull Function<Configuration.Key<?>, ConfigurationLoader<CommentedConfigurationNode>> YAML_LOADER = key -> Configurations.createLoader(key, path -> YamlConfigurationLoader.builder()
    .source(() -> Files.newBufferedReader(path, StandardCharsets.UTF_8))
    .sink(() -> Files.newBufferedWriter(path, StandardCharsets.UTF_8, Configurations.SINK_OPTIONS))
    .defaultOptions(ConfigurationOptions.defaults())
    .build()
  );

  private static final ConcurrentMap<Configuration.Key<?>, Configuration<?, ?>> CONFIGURATIONS = new ConcurrentHashMap<>();
  private static final OpenOption[] SINK_OPTIONS = new OpenOption[] {
    StandardOpenOption.CREATE,
    StandardOpenOption.TRUNCATE_EXISTING,
    StandardOpenOption.WRITE,
    StandardOpenOption.DSYNC
  };

  /**
   * Gets or creates a new {@link Configuration} with the specified {@link ConfigurationLoader},
   * {@link Configuration.Key} and {@link Class} instance type.
   *
   * @param loader the loader supplier
   * @param key the configuration key
   * @param <T> the instance type
   * @param <N> the node type
   * @return the configuration
   * @since 0.5.0
   */
  public static <T, N extends ConfigurationNode> @NonNull Configuration<T, N> getOrCreate(final @NonNull ConfigurationLoader<N> loader,
                                                                                          final Configuration.@NonNull Key<T> key) {
    return Configurations.loadConfiguration(ignored -> loader, key);
  }

  /**
   * Gets or creates a new {@link Configuration} with the specified {@link ConfigurationLoader},
   * {@link Configuration.Key} and {@link Class} instance type.
   *
   * @param loaderSupplier the loader supplier
   * @param key the configuration key
   * @param <T> the instance type
   * @param <N> the node type
   * @return the configuration
   * @since 0.5.0
   */
  public static <T, N extends ConfigurationNode> @NonNull Configuration<T, N> getOrCreate(final @NonNull Function<Configuration.Key<?>, ConfigurationLoader<N>> loaderSupplier,
                                                                                          final Configuration.@NonNull Key<T> key) {
    return Configurations.loadConfiguration(loaderSupplier, key);
  }

  private static <T, N extends ConfigurationNode> @NonNull Configuration<T, N> loadConfiguration(final @NonNull Function<Configuration.Key<?>, ConfigurationLoader<N>> loaderSupplier,
                                                                                                 final Configuration.@NonNull Key<T> key) {
    return (Configuration<T, N>) Configurations.CONFIGURATIONS.computeIfAbsent(key, ignored -> {
      try {
        final Configuration<T, N> configuration = new Configuration<>(key, loaderSupplier.apply(key));
        configuration.load();
        return configuration;
      } catch (final ConfigurateException exception) {
        throw new AssertionError("Unable to load configuration.", exception);
      }
    });
  }

  private static <T, N extends ScopedConfigurationNode<N>, L extends AbstractConfigurationLoader<N>> L createLoader(final Configuration.@NonNull Key<T> key, final @NonNull Function<Path, L> loader) {
    final Path path = key.path();
    try {
      Files.createDirectories(path.getParent());

      return loader.apply(path);
    } catch (final IOException exception) {
      throw new AssertionError("Unable to create configuration directory.", exception);
    }
  }
}
