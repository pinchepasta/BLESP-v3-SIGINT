#ifndef CONFIG_H
#define CONFIG_H

/* Use POSIX threads */
#define THREADS_POSIX 1

/* Use Netlink for hotplug */
#define HAVE_LIBNL 0
#define OS_LINUX 1

/* Features */
#define ENABLE_LOGGING 1
#define ENABLE_DEBUG_LOGGING 1

#endif /* CONFIG_H */
