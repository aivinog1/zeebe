/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static org.rocksdb.TablePropertiesCollectorFactory.NewCompactOnDeletionCollectorFactory;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.rocksdb.Loggers;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.rocksdb.Checkpoint;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.Env;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksObject;
import org.rocksdb.TablePropertiesCollectorFactory;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;

public class ZeebeTransactionDb<ColumnFamilyNames extends Enum<ColumnFamilyNames>>
    implements ZeebeDb<ColumnFamilyNames>, TransactionRenovator {

  private static final Logger LOG = Loggers.DB_LOGGER;
  private static final String ERROR_MESSAGE_CLOSE_RESOURCE =
      "Expected to close RocksDB resource successfully, but exception was thrown. Will continue to close remaining resources.";
  private final OptimisticTransactionDB optimisticTransactionDB;

  private final List<AutoCloseable> closables;

  private final ReadOptions prefixReadOptions;
  private final ReadOptions defaultReadOptions;
  private final WriteOptions defaultWriteOptions;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final Map<String, ColumnFamilyHandle> columnFamilyHandleMap;
  private final Map<String, Long> columnFamilyNativeHandleMap;

  protected ZeebeTransactionDb(
      final List<ColumnFamilyHandle> columnFamilyHandleList,
      final OptimisticTransactionDB optimisticTransactionDB,
      final List<AutoCloseable> closables,
      final RocksDbConfiguration rocksDbConfiguration,
      final ConsistencyChecksSettings consistencyChecksSettings) {
    this.columnFamilyHandleMap =
        columnFamilyHandleList.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    columnFamilyHandle -> {
                      try {
                        return new String(columnFamilyHandle.getName(), StandardCharsets.UTF_8);
                      } catch (RocksDBException e) {
                        throw new RuntimeException(e);
                      }
                    },
                    Function.identity()));
    this.columnFamilyNativeHandleMap =
        columnFamilyHandleMap.entrySet().stream()
            .map(entry -> new SimpleImmutableEntry<>(entry.getKey(), getNativeHandle(entry.getValue())))
            .collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue));
    this.optimisticTransactionDB = optimisticTransactionDB;
    this.closables = closables;
    this.consistencyChecksSettings = consistencyChecksSettings;

    prefixReadOptions =
        new ReadOptions()
            .setPrefixSameAsStart(true)
            .setTotalOrderSeek(false)
            // setting a positive value to read-ahead is only useful when using network storage with
            // high latency, at the cost of making iterators more expensive (memory and computation
            // wise)
//                .setReadaheadSize(0)
            .setReadaheadSize(10 * 1024)
            .setAsyncIo(true)
    ;
    closables.add(prefixReadOptions);
    defaultReadOptions = new ReadOptions().setReadaheadSize(10 * 1024).setAsyncIo(true);
    closables.add(defaultReadOptions);
    defaultWriteOptions = new WriteOptions().setDisableWAL(rocksDbConfiguration.isWalDisabled());
    closables.add(defaultWriteOptions);
  }

  public OptimisticTransactionDB getOptimisticTransactionDB() {
    return optimisticTransactionDB;
  }

  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeTransactionDb<ColumnFamilyNames> openTransactionalDb(
          final RocksDbOptions options,
          final String path,
          final List<AutoCloseable> closables,
          final RocksDbConfiguration rocksDbConfiguration,
          final ConsistencyChecksSettings consistencyChecksSettings)
          throws RocksDBException {
    //    final var cfDescriptors =
    //            Arrays.asList( // todo: could consider using List.of
    //                defaultColumnFamilyDescriptor);
    //    final List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
    final Map<ZbColumnFamilies, ColumnFamilyDescriptor> columnFamilyDescriptorMap =
        Arrays.stream(ZbColumnFamilies.values())
            .map(
                zbColumnFamilies -> {
                  final ColumnFamilyOptions columnFamilyOptions = options.cfOptions();
                  return new SimpleImmutableEntry<>(
                      zbColumnFamilies,
                      new ColumnFamilyDescriptor(
                          zbColumnFamilies.name().getBytes(StandardCharsets.UTF_8),
                          columnFamilyOptions));
                })
            .collect(
                Collectors.toUnmodifiableMap(
                    SimpleImmutableEntry::getKey, SimpleImmutableEntry::getValue));
    final TablePropertiesCollectorFactory tablePropertiesCollectorFactory =
        NewCompactOnDeletionCollectorFactory(100000, 500, 0.1);
    final Options outerOptions =
        new Options(options.dbOptions(), options.cfOptions())
            .setCreateIfMissing(true)
            .setCreateMissingColumnFamilies(false);
    final List<TablePropertiesCollectorFactory> tablePropertiesCollectorFactories = List.of(NewCompactOnDeletionCollectorFactory(100000, 500, 0.1));
    outerOptions.setTablePropertiesCollectorFactory(tablePropertiesCollectorFactories);
    final OptimisticTransactionDB optimisticTransactionDB =
        OptimisticTransactionDB.open(outerOptions, path);
    //    final OptimisticTransactionDB optimisticTransactionDB =
    //        OptimisticTransactionDB.open(options.dbOptions(), path, cfDescriptors, cfHandles);
//    final ColumnFamilyHandle zeebeColumnFamilyHandle =
//        optimisticTransactionDB.createColumnFamily(defaultColumnFamilyDescriptor);
    final List<ColumnFamilyDescriptor> columnFamilyDescriptors =
        columnFamilyDescriptorMap.values().stream().toList();
    final List<ColumnFamilyHandle> columnFamilyHandleList =
        optimisticTransactionDB.createColumnFamilies(columnFamilyDescriptors);
    closables.addAll(columnFamilyHandleList);
//    closables.add(zeebeColumnFamilyHandle);
    closables.addAll(tablePropertiesCollectorFactories);
    closables.add(outerOptions);
    closables.add(tablePropertiesCollectorFactory);
    closables.add(optimisticTransactionDB);

    //    if (cfHandles.size() != 1) {
    //      throw new IllegalStateException(
    //          "Expected a handle for the default column family but found %d handles"
    //              .formatted(cfHandles.size()));
    //    }

    //    final ColumnFamilyHandle defaultColumnFamilyHandle = cfHandles.getFirst();

//    closables.add(zeebeColumnFamilyHandle);

    return new ZeebeTransactionDb<>(
        columnFamilyHandleList,
        optimisticTransactionDB,
        closables,
        rocksDbConfiguration,
        consistencyChecksSettings);
  }

  static long getNativeHandle(final RocksObject object) {
    try {
      return RocksDbInternal.nativeHandle.getLong(object);
    } catch (final IllegalAccessException e) {
      throw new RuntimeException(
          "Unexpected error occurred trying to access private nativeHandle_ field", e);
    }
  }

  protected ReadOptions getPrefixReadOptions() {
    return prefixReadOptions;
  }

  protected ColumnFamilyHandle getHandle(final String columnFamilyName) {
    return columnFamilyHandleMap.get(columnFamilyName);
  }

  protected long getReadOptionsNativeHandle() {
    return getNativeHandle(defaultReadOptions);
  }

  protected long getNativeHandle(final String name) {
    return columnFamilyNativeHandleMap.get(name);
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyNames columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    return new TransactionalColumnFamily<>(
        this, consistencyChecksSettings, columnFamily, context, keyInstance, valueInstance);
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue> ColumnFamily<KeyType, ValueType> createColumnFamily(
      final ColumnFamilyNames columnFamily, final TransactionContext context,
      final KeyType keyInstance, final ValueType valueInstance,
      final boolean isSingleDeletePreferred) {
    return new TransactionalColumnFamily<>(
        this, consistencyChecksSettings, columnFamily, context, keyInstance, valueInstance, isSingleDeletePreferred);
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    try (final Checkpoint checkpoint = Checkpoint.create(optimisticTransactionDB)) {
      try {
        checkpoint.createCheckpoint(snapshotDir.getAbsolutePath());
      } catch (final RocksDBException rocksException) {
        throw new ZeebeDbException(
            String.format("Failed to take snapshot in path %s.", snapshotDir), rocksException);
      }
    }
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    String propertyValue = null;
    try {
      propertyValue = optimisticTransactionDB.getProperty(columnFamilyHandleMap.get(ZbColumnFamilies.DEFAULT.name()), propertyName);
    } catch (final RocksDBException rde) {
      LOG.debug(rde.getMessage(), rde);
    }
    return Optional.ofNullable(propertyValue);
  }

  @Override
  public TransactionContext createContext() {
    final Transaction transaction = optimisticTransactionDB.beginTransaction(defaultWriteOptions);
    final ZeebeTransaction zeebeTransaction =
        new ZeebeTransaction(transaction, this, optimisticTransactionDB);
    closables.add(zeebeTransaction);
    return new DefaultTransactionContext(zeebeTransaction);
  }

  @Override
  public boolean isEmpty(
      final ColumnFamilyNames columnFamilyName, final TransactionContext context) {
    return createColumnFamily(columnFamilyName, context, DbNullKey.INSTANCE, DbNil.INSTANCE)
        .isEmpty();
  }

  @Override
  public Transaction renewTransaction(final Transaction oldTransaction) {
    return optimisticTransactionDB.beginTransaction(defaultWriteOptions, oldTransaction);
  }

  @Override
  public void close() {
    // Correct order of closing
    // 1. transaction
    // 2. options
    // 3. column family handles
    // 4. database
    // 5. db options
    // 6. column family options
    // https://github.com/facebook/rocksdb/wiki/RocksJava-Basics#opening-a-database-with-column-families
    Collections.reverse(closables);
    closables.forEach(
        closable -> {
          try {
            closable.close();
          } catch (final Exception e) {
            LOG.error(ERROR_MESSAGE_CLOSE_RESOURCE, e);
          }
        });
  }
}
