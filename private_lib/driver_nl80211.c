/*
 * driver_mac80211_nl.c
 *
 * Copyright 2001-2010 Texas Instruments, Inc. - http://www.ti.com/
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

#include "includes.h"
#include <sys/ioctl.h>
#include <net/if_arp.h>
#include <net/if.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <netlink/genl/genl.h>
#include <netlink/genl/family.h>
#include <netlink/genl/ctrl.h>
#include <netlink/msg.h>
#include <netlink/attr.h>

#include "wireless_copy.h"
#include "common.h"
#include "driver.h"
#include "eloop.h"
#include "driver_wext.h"
#include "ieee802_11_defs.h"
#include "wpa_common.h"
#include "wpa_ctrl.h"
#include "wpa_supplicant_i.h"
#include "config_ssid.h"
#include "wpa_debug.h"
#include "linux_ioctl.h"
#include "driver_nl80211.h"

#define WPA_EVENT_DRIVER_STATE          "CTRL-EVENT-DRIVER-STATE "
#define DRV_NUMBER_SEQUENTIAL_ERRORS     4

#define WPA_PS_ENABLED   0
#define WPA_PS_DISABLED  1

static int g_drv_errors = 0;
static int g_power_mode = 0;

int send_and_recv_msgs(struct wpa_driver_nl80211_data *drv, struct nl_msg *msg,
                       int (*valid_handler)(struct nl_msg *, void *),
                       void *valid_data);

static void wpa_driver_send_hang_msg(struct wpa_driver_nl80211_data *drv)
{
	g_drv_errors++;
	if (g_drv_errors > DRV_NUMBER_SEQUENTIAL_ERRORS) {
		g_drv_errors = 0;
		wpa_msg(drv->ctx, MSG_INFO, WPA_EVENT_DRIVER_STATE "HANGED");
	}
}

static int wpa_driver_set_power_save(void *priv, int state)
{
	struct i802_bss *bss = priv;
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct nl_msg *msg;
	int ret = -1;
	enum nl80211_ps_state ps_state;

	msg = nlmsg_alloc();
	if (!msg)
		return -1;

	genlmsg_put(msg, 0, 0, genl_family_get_id(drv->nl80211), 0, 0,
		    NL80211_CMD_SET_POWER_SAVE, 0);

	if (state == WPA_PS_ENABLED)
		ps_state = NL80211_PS_ENABLED;
	else
		ps_state = NL80211_PS_DISABLED;

	NLA_PUT_U32(msg, NL80211_ATTR_IFINDEX, drv->ifindex);
	NLA_PUT_U32(msg, NL80211_ATTR_PS_STATE, ps_state);

	ret = send_and_recv_msgs(drv, msg, NULL, NULL);
	msg = NULL;
	if (ret < 0)
		wpa_printf(MSG_ERROR, "nl80211: Set power mode fail: %d", ret);
nla_put_failure:
	nlmsg_free(msg);
	return ret;
}

int wpa_driver_nl80211_driver_cmd(void *priv, char *cmd, char *buf,
				  size_t buf_len )
{
	struct i802_bss *bss = priv;
	struct wpa_driver_nl80211_data *drv = bss->drv;
	struct ifreq ifr;
	int ret = 0;

	if (os_strcasecmp(cmd, "STOP") == 0) {
		linux_set_iface_flags(drv->ioctl_sock, bss->ifname, 0);
		wpa_msg(drv->ctx, MSG_INFO, WPA_EVENT_DRIVER_STATE "STOPPED");
	} else if (os_strcasecmp(cmd, "START") == 0) {
		linux_set_iface_flags(drv->ioctl_sock, bss->ifname, 1);
		wpa_msg(drv->ctx, MSG_INFO, WPA_EVENT_DRIVER_STATE "STARTED");
	} else if (os_strcasecmp(cmd, "RELOAD") == 0) {
		wpa_msg(drv->ctx, MSG_INFO, WPA_EVENT_DRIVER_STATE "HANGED");
	} else if (os_strncasecmp(cmd, "POWERMODE ", 10) == 0) {
		int mode;
		mode = atoi(cmd + 10);
		ret = wpa_driver_set_power_save(priv, mode);
		if (ret < 0) {
			wpa_driver_send_hang_msg(drv);
		} else {
			g_power_mode = mode;
			g_drv_errors = 0;
		}
	} else if (os_strncasecmp(cmd, "GETPOWER", 8) == 0) {
		ret = os_snprintf(buf, buf_len, "POWERMODE = %d\n", g_power_mode);
	} else if (os_strcasecmp(cmd, "MACADDR") == 0) {
		u8 macaddr[ETH_ALEN] = {};

		ret = linux_get_ifhwaddr(drv->ioctl_sock, bss->ifname, macaddr);
		if (!ret)
			ret = os_snprintf(buf, buf_len,
					  "Macaddr = " MACSTR "\n", MAC2STR(macaddr));
	} else {
		wpa_printf(MSG_INFO, "%s: Unsupported command %s", __func__, cmd);
	}
	return ret;
}
