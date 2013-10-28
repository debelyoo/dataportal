package controllers.util

import models.Device

trait LinkedToDevice {
  def device: Device
}
