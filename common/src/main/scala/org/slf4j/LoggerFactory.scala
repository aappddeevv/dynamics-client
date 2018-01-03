// Copyright (c) 2017 The Trapelo Group LLC
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package org.slf4j

import io.scalajs.nodejs.process

trait LoggerFactory {
  def loggingLevel_=(v: String): Unit
  def loggingLevel: String
  def levelEnableFor(name: String, check: String): Boolean
  def levelEnableFor(marker: Marker, name: String, check: String): Boolean
}

object LoggerFactory extends LoggerFactory {

  // static final String CODES_PREFIX = "http://www.slf4j.org/codes.html";

  // static final String NO_PROVIDERS_URL = CODES_PREFIX + "#noProviders";
  // static final String IGNORED_BINDINGS_URL = CODES_PREFIX + "#ignoredBindings";

  // static final String NO_STATICLOGGERBINDER_URL = CODES_PREFIX + "#StaticLoggerBinder";
  // static final String MULTIPLE_BINDINGS_URL = CODES_PREFIX + "#multiple_bindings";
  // static final String NULL_LF_URL = CODES_PREFIX + "#null_LF";
  // static final String VERSION_MISMATCH = CODES_PREFIX + "#version_mismatch";
  // static final String SUBSTITUTE_LOGGER_URL = CODES_PREFIX + "#substituteLogger";
  // static final String LOGGER_NAME_MISMATCH_URL = CODES_PREFIX + "#loggerNameMismatch";
  // static final String REPLAY_URL = CODES_PREFIX + "#replay";

  // static final String UNSUCCESSFUL_INIT_URL = CODES_PREFIX + "#unsuccessfulInit";
  // static final String UNSUCCESSFUL_INIT_MSG = "org.slf4j.LoggerFactory in failed state. Original exception was thrown EARLIER. See also "
  //                 + UNSUCCESSFUL_INIT_URL;

  // static final int UNINITIALIZED = 0;
  // static final int ONGOING_INITIALIZATION = 1;
  // static final int FAILED_INITIALIZATION = 2;
  // static final int SUCCESSFUL_INITIALIZATION = 3;
  // static final int NOP_FALLBACK_INITIALIZATION = 4;

  // static volatile int INITIALIZATION_STATE = UNINITIALIZED;
  // static final SubstitureServiceProvider SUBST_PROVIDER = new SubstitureServiceProvider();
  // static final NOPServiceProvider NOP_FALLBACK_FACTORY = new NOPServiceProvider();

  // // Support for detecting mismatched logger names.
  // static final String DETECT_LOGGER_NAME_MISMATCH_PROPERTY = "slf4j.detectLoggerNameMismatch";
  // static final String JAVA_VENDOR_PROPERTY = "java.vendor.url";

  // static boolean DETECT_LOGGER_NAME_MISMATCH = Util.safeGetBooleanSystemProperty(DETECT_LOGGER_NAME_MISMATCH_PROPERTY);

  // static volatile SLF4JServiceProvider PROVIDER;

  // private static List<SLF4JServiceProvider> findServiceProviders() {
  //     ServiceLoader<SLF4JServiceProvider> serviceLoader = ServiceLoader.load(SLF4JServiceProvider.class);
  //     List<SLF4JServiceProvider> providerList = new ArrayList<SLF4JServiceProvider>();
  //     for (SLF4JServiceProvider provider : serviceLoader) {
  //         providerList.add(provider);
  //     }
  //     return providerList;
  // }

  /**
    * Force LoggerFactory to consider itself uninitialized.
    * <p/>
    * <p/>
    * This method is intended to be called by classes (in the same package) for
    * testing purposes. This method is internal. It can be modified, renamed or
    * removed at any time without notice.
    * <p/>
    * <p/>
    * You are strongly discouraged from calling this method in production code.
    */
  // static void reset() {
  //     INITIALIZATION_STATE = UNINITIALIZED;
  // }

  // private final static void performInitialization() {
  //     bind();
  //     if (INITIALIZATION_STATE == SUCCESSFUL_INITIALIZATION) {
  //         versionSanityCheck();
  //     }
  // }

  private def bind(): Unit = {
    // try {
    //     List<SLF4JServiceProvider> providersList = findServiceProviders();
    //     reportMultipleBindingAmbiguity(providersList);
    //     if (providersList != null && !providersList.isEmpty()) {
    //     	PROVIDER = providersList.get(0);
    //     	PROVIDER.initialize();
    //     	INITIALIZATION_STATE = SUCCESSFUL_INITIALIZATION;
    //         reportActualBinding(providersList);
    //         fixSubstituteLoggers();
    //         replayEvents();
    //         // release all resources in SUBST_FACTORY
    //         SUBST_PROVIDER.getSubstituteLoggerFactory().clear();
    //     } else {
    //         INITIALIZATION_STATE = NOP_FALLBACK_INITIALIZATION;
    //         Util.report("No SLF4J providers were found.");
    //         Util.report("Defaulting to no-operation (NOP) logger implementation");
    //         Util.report("See " + NO_PROVIDERS_URL + " for further details.");

    //         Set<URL> staticLoggerBinderPathSet = findPossibleStaticLoggerBinderPathSet();
    //         reportIgnoredStaticLoggerBinders(staticLoggerBinderPathSet);
    //     }
    // } catch (Exception e) {
    //     failedBinding(e);
    //     throw new IllegalStateException("Unexpected initialization failure", e);
    // }
  }

  def getLogger(name: String): Logger = {
    // ILoggerFactory iLoggerFactory = getILoggerFactory();
    // return iLoggerFactory.getLogger(name);
    new SimpleLogger(name, this)
  }

  private var level: String = process.env.get("LOGGING_LEVEL").getOrElse("")

  def loggingLevel_=(v: String): Unit = level = v
  def loggingLevel: String            = level

  // todo check level hierarchy...
  def levelEnableFor(name: String, check: String): Boolean =
    check.toUpperCase == level.toUpperCase

  def levelEnableFor(marker: Marker, name: String, check: String): Boolean =
    check.toUpperCase == level.toUpperCase
  // public static Logger getLogger(Class<?> clazz) {
  //     Logger logger = getLogger(clazz.getName());
  //     return logger;
  // }

  // public static ILoggerFactory getILoggerFactory() {
  //     return getProvider().getLoggerFactory();
  // }

  // static SLF4JServiceProvider getProvider() {
  //     if (INITIALIZATION_STATE == UNINITIALIZED) {
  //         synchronized (LoggerFactory.class) {
  //             if (INITIALIZATION_STATE == UNINITIALIZED) {
  //                 INITIALIZATION_STATE = ONGOING_INITIALIZATION;
  //                 performInitialization();
  //             }
  //         }
  //     }
  //     switch (INITIALIZATION_STATE) {
  //     case SUCCESSFUL_INITIALIZATION:
  //         return PROVIDER;
  //     case NOP_FALLBACK_INITIALIZATION:
  //         return NOP_FALLBACK_FACTORY;
  //     case FAILED_INITIALIZATION:
  //         throw new IllegalStateException(UNSUCCESSFUL_INIT_MSG);
  //     case ONGOING_INITIALIZATION:
  //         // support re-entrant behavior.
  //         // See also http://jira.qos.ch/browse/SLF4J-97
  //         return SUBST_PROVIDER;
  //     }
  //     throw new IllegalStateException("Unreachable code");
  // }
}
