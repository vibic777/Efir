package ru.efir.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Значки приложения: контуры из Material Icons (Apache License 2.0,
 * `docs/licenses/Apache-2.0.txt`), встроенные данными путей. Подключать всю
 * библиотеку `material-icons-extended` ради трёх десятков значков незачем.
 */
object AppIcons {
    val Delete: ImageVector by lazy { icon("Delete", "M16,9v10H8V9h8m-1.5,-6h-5l-1,1H5v2h14V4h-3.5l-1,-1ZM18,7H6v12c0,1.1,0.9,2,2,2h8c1.1,0,2,-0.9,2,-2V7Z") }
    val History: ImageVector by lazy { icon("History", "M13,3c-4.97,0,-9,4.03,-9,9L1,12l3.89,3.89l0.07,0.14L9,12L6,12c0,-3.87,3.13,-7,7,-7s7,3.13,7,7s-3.13,7,-7,7c-1.93,0,-3.68,-0.79,-4.94,-2.06l-1.42,1.42C8.27,19.99,10.51,21,13,21c4.97,0,9,-4.03,9,-9s-4.03,-9,-9,-9ZM12,8v5l4.25,2.52l0.77,-1.28l-3.52,-2.09L13.5,8Z") }
    val Tune: ImageVector by lazy { icon("Tune", "M3,17v2h6v-2L3,17ZM3,5v2h10L13,5L3,5ZM13,21v-2h8v-2h-8v-2h-2v6h2ZM7,9v2L3,11v2h4v2h2L9,9L7,9ZM21,13v-2L11,11v2h10ZM15,9h2L17,7h4L21,5h-4L17,3h-2v6Z") }
    val Edit: ImageVector by lazy { icon("Edit", "M14.06,9.02l0.92,0.92L5.92,19L5,19v-0.92l9.06,-9.06M17.66,3c-0.25,0,-0.51,0.1,-0.7,0.29l-1.83,1.83l3.75,3.75l1.83,-1.83c0.39,-0.39,0.39,-1.02,0,-1.41l-2.34,-2.34c-0.2,-0.2,-0.45,-0.29,-0.71,-0.29ZM14.06,6.19L3,17.25L3,21h3.75L17.81,9.94l-3.75,-3.75Z") }
    val ContentCopy: ImageVector by lazy { icon("ContentCopy", "M16,1L4,1c-1.1,0,-2,0.9,-2,2v14h2L4,3h12L16,1ZM19,5L8,5c-1.1,0,-2,0.9,-2,2v14c0,1.1,0.9,2,2,2h11c1.1,0,2,-0.9,2,-2L21,7c0,-1.1,-0.9,-2,-2,-2ZM19,21L8,21L8,7h11v14Z") }
    val Visibility: ImageVector by lazy { icon("Visibility", "M12,6c3.79,0,7.17,2.13,8.82,5.5C19.17,14.87,15.79,17,12,17s-7.17,-2.13,-8.82,-5.5C4.83,8.13,8.21,6,12,6m0,-2C7,4,2.73,7.11,1,11.5C2.73,15.89,7,19,12,19s9.27,-3.11,11,-7.5C21.27,7.11,17,4,12,4ZM12,9c1.38,0,2.5,1.12,2.5,2.5S13.38,14,12,14s-2.5,-1.12,-2.5,-2.5S10.62,9,12,9m0,-2c-2.48,0,-4.5,2.02,-4.5,4.5S9.52,16,12,16s4.5,-2.02,4.5,-4.5S14.48,7,12,7Z") }
    val Lock: ImageVector by lazy { icon("Lock", "M18,8h-1L17,6c0,-2.76,-2.24,-5,-5,-5S7,3.24,7,6v2L6,8c-1.1,0,-2,0.9,-2,2v10c0,1.1,0.9,2,2,2h12c1.1,0,2,-0.9,2,-2L20,10c0,-1.1,-0.9,-2,-2,-2ZM9,6c0,-1.66,1.34,-3,3,-3s3,1.34,3,3v2L9,8L9,6ZM18,20L6,20L6,10h12v10ZM12,17c1.1,0,2,-0.9,2,-2s-0.9,-2,-2,-2s-2,0.9,-2,2s0.9,2,2,2Z") }
    val FileDownload: ImageVector by lazy { icon("FileDownload", "M18,15v3H6v-3H4v3c0,1.1,0.9,2,2,2h12c1.1,0,2,-0.9,2,-2v-3H18ZM17,11l-1.41,-1.41L13,12.17V4h-2v8.17L8.41,9.59L7,11l5,5L17,11Z") }
    val Description: ImageVector by lazy { icon("Description", "M14,2H6c-1.1,0,-1.99,0.9,-1.99,2L4,20c0,1.1,0.89,2,1.99,2H18c1.1,0,2,-0.9,2,-2V8l-6,-6ZM16,18H8v-2h8v2ZM16,14H8v-2h8v2ZM13,9V3.5L18.5,9H13Z") }
    val Mic: ImageVector by lazy { icon("Mic", "M12,14c1.66,0,2.99,-1.34,2.99,-3L15,5c0,-1.66,-1.34,-3,-3,-3S9,3.34,9,5v6c0,1.66,1.34,3,3,3ZM17.3,11c0,3,-2.54,5.1,-5.3,5.1S6.7,14,6.7,11H5c0,3.41,2.72,6.23,6,6.72V21h2v-3.28c3.28,-0.48,6,-3.3,6,-6.72h-1.7Z") }
    val PhotoCamera: ImageVector by lazy { icon("PhotoCamera", "M9,2L7.17,4H4c-1.1,0,-2,0.9,-2,2v12c0,1.1,0.9,2,2,2h16c1.1,0,2,-0.9,2,-2V6c0,-1.1,-0.9,-2,-2,-2h-3.17L15,2H9ZM12,17c-2.76,0,-5,-2.24,-5,-5s2.24,-5,5,-5s5,2.24,5,5s-2.24,5,-5,5ZM12,8.8c-1.77,0,-3.2,1.43,-3.2,3.2s1.43,3.2,3.2,3.2s3.2,-1.43,3.2,-3.2s-1.43,-3.2,-3.2,-3.2Z") }
    val Videocam: ImageVector by lazy { icon("Videocam", "M17,10.5V7c0,-0.55,-0.45,-1,-1,-1H4c-0.55,0,-1,0.45,-1,1v10c0,0.55,0.45,1,1,1h12c0.55,0,1,-0.45,1,-1v-3.5l4,4v-11l-4,4Z") }
    val ScreenRecord: ImageVector by lazy { icon("ScreenRecord", "M21,2H3c-1.1,0,-2,0.9,-2,2v12c0,1.1,0.9,2,2,2h7v2H8v2h8v-2h-2v-2h7c1.1,0,2,-0.9,2,-2V4c0,-1.1,-0.9,-2,-2,-2ZM21,16H3V4h18v12ZM12,7c-1.66,0,-3,1.34,-3,3s1.34,3,3,3s3,-1.34,3,-3s-1.34,-3,-3,-3Z") }
    val Folder: ImageVector by lazy { icon("Folder", "M10,4H4c-1.1,0,-1.99,0.9,-1.99,2L2,18c0,1.1,0.9,2,2,2h16c1.1,0,2,-0.9,2,-2V8c0,-1.1,-0.9,-2,-2,-2h-8l-2,-2Z") }
    val NewFolder: ImageVector by lazy { icon("NewFolder", "M20,6h-8l-2,-2H4c-1.11,0,-1.99,0.89,-1.99,2L2,18c0,1.11,0.89,2,2,2h16c1.11,0,2,-0.89,2,-2V8c0,-1.11,-0.89,-2,-2,-2ZM19,14h-3v3h-2v-3h-3v-2h3V9h2v3h3v2Z") }
    val ViewGrid: ImageVector by lazy { icon("ViewGrid", "M3,3v8h8V3H3ZM3,13v8h8v-8H3ZM13,3v8h8V3h-8ZM13,13v8h8v-8h-8Z") }
    val Schedule: ImageVector by lazy { icon("Schedule", "M11.99,2C6.47,2,2,6.48,2,12s4.47,10,9.99,10C17.52,22,22,17.52,22,12S17.52,2,11.99,2ZM12,20c-4.42,0,-8,-3.58,-8,-8s3.58,-8,8,-8s8,3.58,8,8s-3.58,8,-8,8ZM12.5,7H11v6l5.25,3.15l0.75,-1.23l-4.5,-2.67V7Z") }
    val Fullscreen: ImageVector by lazy { icon("Fullscreen", "M7,14H5v5h5v-2H7v-3ZM5,10h2V7h3V5H5v5ZM17,17h-3v2h5v-5h-2v3ZM14,5v2h3v3h2V5h-5Z") }
    val FullscreenExit: ImageVector by lazy { icon("FullscreenExit", "M5,16h3v3h2v-5H5v2ZM8,8H5v2h5V5H8v3ZM14,19h2v-3h3v-2h-5v5ZM16,8V5h-2v5h5V8h-3Z") }
    val RotateRight: ImageVector by lazy { icon("RotateRight", "M15.55,5.55L11,1v3.07C7.06,4.56,4,7.92,4,12s3.05,7.44,7,7.93v-2.02c-2.84,-0.48,-5,-2.94,-5,-5.91s2.16,-5.43,5,-5.91V10l4.55,-4.45ZM19.93,11c-0.17,-1.39,-0.72,-2.73,-1.62,-3.89l-1.42,1.42c0.54,0.75,0.88,1.6,1.02,2.47h2.02ZM13,17.9v2.02c1.39,-0.17,2.74,-0.71,3.9,-1.61l-1.44,-1.44c-0.75,0.54,-1.59,0.89,-2.46,1.03ZM16.89,15.48l1.42,1.41c0.9,-1.16,1.45,-2.5,1.62,-3.89h-2.02c-0.14,0.87,-0.48,1.72,-1.02,2.48Z") }
    val Close: ImageVector by lazy { icon("Close", "M19,6.41L17.59,5L12,10.59L6.41,5L5,6.41L10.59,12L5,17.59L6.41,19L12,13.41L17.59,19L19,17.59L13.41,12Z") }
    val Check: ImageVector by lazy { icon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19L21,7l-1.41,-1.41Z") }
    val Add: ImageVector by lazy { icon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2Z") }
    val ArrowBack: ImageVector by lazy { icon("ArrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8l8,8l1.41,-1.41L7.83,13H20v-2Z") }
    val Search: ImageVector by lazy { icon("Search", "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59,16,11.11,16,9.5C16,5.91,13.09,3,9.5,3S3,5.91,3,9.5S5.91,16,9.5,16c1.61,0,3.09,-0.59,4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5ZM9.5,14C7.01,14,5,11.99,5,9.5S7.01,5,9.5,5S14,7.01,14,9.5S11.99,14,9.5,14Z") }
    val Refresh: ImageVector by lazy { icon("Refresh", "M17.65,6.35C16.2,4.9,14.21,4,12,4c-4.42,0,-7.99,3.58,-7.99,8s3.57,8,7.99,8c3.73,0,6.84,-2.55,7.73,-6h-2.08c-0.82,2.33,-3.04,4,-5.65,4c-3.31,0,-6,-2.69,-6,-6s2.69,-6,6,-6c1.66,0,3.14,0.69,4.22,1.78L13,11h7V4l-2.35,2.35Z") }
    val FileUpload: ImageVector by lazy { icon("FileUpload", "M9,16h6v-6h4l-7,-7l-7,7h4v6ZM5,18h14v2H5v-2Z") }
    val PlayArrow: ImageVector by lazy { icon("PlayArrow", "M8,5v14l11,-7Z") }
    val Stop: ImageVector by lazy { icon("Stop", "M6,6h12v12H6Z") }
    val Info: ImageVector by lazy { icon("Info", "M12,2C6.48,2,2,6.48,2,12s4.48,10,10,10s10,-4.48,10,-10S17.52,2,12,2ZM13,17h-2v-6h2v6ZM13,9h-2V7h2v2Z") }
    val Send: ImageVector by lazy { icon("Send", "M2.01,21L23,12L2.01,3L2,10l15,2l-15,2Z") }
    val Sync: ImageVector by lazy { icon("Sync", "M12,4V1L8,5l4,4V6c3.31,0,6,2.69,6,6c0,1.01,-0.25,1.97,-0.7,2.8l1.46,1.46C19.54,15.03,20,13.57,20,12c0,-4.42,-3.58,-8,-8,-8ZM12,18c-3.31,0,-6,-2.69,-6,-6c0,-1.01,0.25,-1.97,0.7,-2.8L5.24,7.74C4.46,8.97,4,10.43,4,12c0,4.42,3.58,8,8,8v3l4,-4l-4,-4v3Z") }
    val Key: ImageVector by lazy { icon("Key", "M12.65,10C11.83,7.67,9.61,6,7,6c-3.31,0,-6,2.69,-6,6s2.69,6,6,6c2.61,0,4.83,-1.67,5.65,-4H17v4h4v-4h2v-4H12.65ZM7,14c-1.1,0,-2,-0.9,-2,-2s0.9,-2,2,-2s2,0.9,2,2s-0.9,2,-2,2Z") }
    val Notifications: ImageVector by lazy { icon("Notifications", "M12,22c1.1,0,2,-0.9,2,-2h-4c0,1.1,0.89,2,2,2ZM18,16v-5c0,-3.07,-1.64,-5.64,-4.5,-6.32V4c0,-0.83,-0.67,-1.5,-1.5,-1.5s-1.5,0.67,-1.5,1.5v0.68C7.63,5.36,6,7.92,6,11v5l-2,2v1h16v-1l-2,-2Z") }
    val NotificationsOff: ImageVector by lazy { icon("NotificationsOff", "M12,22c1.1,0,2,-0.9,2,-2h-4c0,1.1,0.9,2,2,2ZM12,6.5c2.49,0,4,2.02,4,4.5v0.1l2,2L18,11c0,-3.07,-1.63,-5.64,-4.5,-6.32L13.5,4c0,-0.83,-0.67,-1.5,-1.5,-1.5s-1.5,0.67,-1.5,1.5v0.68c-0.24,0.06,-0.47,0.15,-0.69,0.23l1.64,1.64c0.18,-0.02,0.36,-0.05,0.55,-0.05ZM5.41,3.35L4,4.76l2.81,2.81C6.29,8.57,6,9.74,6,11v5l-2,2v1h14.24l1.74,1.74l1.41,-1.41L5.41,3.35ZM16,17L8,17v-6c0,-0.68,0.12,-1.32,0.34,-1.9L16,16.76L16,17Z") }
    val VolumeOff: ImageVector by lazy { icon("VolumeOff", "M16.5,12c0,-1.77,-1.02,-3.29,-2.5,-4.03v2.21l2.45,2.45c0.03,-0.2,0.05,-0.41,0.05,-0.63ZM19,12c0,0.94,-0.2,1.82,-0.54,2.64l1.51,1.51C20.63,14.91,21,13.5,21,12c0,-4.28,-2.99,-7.86,-7,-8.77v2.06c2.89,0.86,5,3.54,5,6.71ZM4.27,3L3,4.27L7.73,9H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52,-1.42,0.93,-2.25,1.18v2.06c1.38,-0.31,2.63,-0.95,3.69,-1.81L19.73,21L21,19.73l-9,-9L4.27,3ZM12,4L9.91,6.09L12,8.18V4Z") }
    val MoreVert: ImageVector by lazy { icon("MoreVert", "M12,8c1.1,0,2,-0.9,2,-2s-0.9,-2,-2,-2s-2,0.9,-2,2s0.9,2,2,2ZM12,10c-1.1,0,-2,0.9,-2,2s0.9,2,2,2s2,-0.9,2,-2s-0.9,-2,-2,-2ZM12,16c-1.1,0,-2,0.9,-2,2s0.9,2,2,2s2,-0.9,2,-2s-0.9,-2,-2,-2Z") }
    val Help: ImageVector by lazy { icon("Help", "M12,2C6.48,2,2,6.48,2,12s4.48,10,10,10s10,-4.48,10,-10S17.52,2,12,2ZM13,19h-2v-2h2v2ZM15.07,11.25l-0.9,0.92C13.45,12.9,13,13.5,13,15h-2v-0.5c0,-1.1,0.45,-2.1,1.17,-2.83l1.24,-1.26c0.37,-0.36,0.59,-0.86,0.59,-1.41c0,-1.1,-0.9,-2,-2,-2s-2,0.9,-2,2H8c0,-2.21,1.79,-4,4,-4s4,1.79,4,4c0,0.88,-0.36,1.68,-0.93,2.25Z") }

    val Smartphone: ImageVector by lazy { icon("Smartphone", "M17,1.01L7,1c-1.1,0,-2,0.9,-2,2v18c0,1.1,0.9,2,2,2h10c1.1,0,2,-0.9,2,-2V3c0,-1.1,-0.9,-1.99,-2,-1.99ZM17,19H7V5h10v14Z") }
    val Computer: ImageVector by lazy { icon("Computer", "M20,18c1.1,0,1.99,-0.9,1.99,-2L22,6c0,-1.1,-0.9,-2,-2,-2H4c-1.1,0,-2,0.9,-2,2v10c0,1.1,0.9,2,2,2H0v2h24v-2h-4ZM4,6h16v10H4V6Z") }
    val Call: ImageVector by lazy { icon("Call", "M6.62,10.79c1.44,2.83,3.76,5.14,6.59,6.59l2.2,-2.2c0.27,-0.27,0.67,-0.36,1.02,-0.24c1.12,0.37,2.33,0.57,3.57,0.57c0.55,0,1,0.45,1,1V20c0,0.55,-0.45,1,-1,1c-9.39,0,-17,-7.61,-17,-17c0,-0.55,0.45,-1,1,-1h3.5c0.55,0,1,0.45,1,1c0,1.25,0.2,2.45,0.57,3.57c0.11,0.35,0.03,0.74,-0.25,1.02l-2.2,2.2Z") }
    val CallEnd: ImageVector by lazy { icon("CallEnd", "M12,9c-1.6,0,-3.15,0.25,-4.6,0.72v3.1c0,0.39,-0.23,0.74,-0.56,0.9c-0.98,0.49,-1.87,1.12,-2.66,1.85c-0.18,0.18,-0.43,0.28,-0.7,0.28c-0.28,0,-0.53,-0.11,-0.71,-0.29L0.29,13.08C0.11,12.91,0,12.66,0,12.38c0,-0.28,0.11,-0.53,0.29,-0.71C3.34,8.78,7.46,7,12,7s8.66,1.78,11.71,4.67c0.18,0.18,0.29,0.43,0.29,0.71c0,0.28,-0.11,0.53,-0.29,0.71l-2.48,2.48c-0.18,0.18,-0.43,0.29,-0.71,0.29c-0.27,0,-0.52,-0.11,-0.7,-0.28c-0.79,-0.74,-1.69,-1.36,-2.67,-1.85c-0.33,-0.16,-0.56,-0.5,-0.56,-0.9v-3.1C15.15,9.25,13.6,9,12,9Z") }
    val CallMade: ImageVector by lazy { icon("CallMade", "M9,5v2h6.59L4,18.59L5.41,20L17,8.41V15h2V5Z") }
    val CallReceived: ImageVector by lazy { icon("CallReceived", "M20,5.41L18.59,4L7,15.59V9H5v10h10v-2H8.41Z") }
    val Sms: ImageVector by lazy { icon("Sms", "M20,2H4c-1.1,0,-1.99,0.9,-1.99,2L2,22l4,-4h14c1.1,0,2,-0.9,2,-2V4c0,-1.1,-0.9,-2,-2,-2ZM9,11H7V9h2v2ZM13,11h-2V9h2v2ZM17,11h-2V9h2v2Z") }
    val Home: ImageVector by lazy { icon("Home", "M10,20v-6h4v6h5v-8h3L12,3L2,12h3v8Z") }
    val Apps: ImageVector by lazy { icon("Apps", "M4,8h4V4H4v4ZM10,20h4v-4h-4v4ZM4,20h4v-4H4v4ZM4,14h4v-4H4v4ZM10,14h4v-4h-4v4ZM16,4v4h4V4h-4ZM10,8h4V4h-4v4ZM16,14h4v-4h-4v4ZM16,20h4v-4h-4v4Z") }
    val ContentPaste: ImageVector by lazy { icon("ContentPaste", "M19,2h-4.18C14.4,0.84,13.3,0,12,0c-1.3,0,-2.4,0.84,-2.82,2H5c-1.1,0,-2,0.9,-2,2v16c0,1.1,0.9,2,2,2h14c1.1,0,2,-0.9,2,-2V4c0,-1.1,-0.9,-2,-2,-2ZM12,2c0.55,0,1,0.45,1,1s-0.45,1,-1,1s-1,-0.45,-1,-1s0.45,-1,1,-1ZM19,20H5V4h2v3h10V4h2v16Z") }
    val ArrowUpward: ImageVector by lazy { icon("ArrowUpward", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8l-8,8Z") }
    val Link: ImageVector by lazy { icon("Link", "M3.9,12c0,-1.71,1.39,-3.1,3.1,-3.1h4V7H7c-2.76,0,-5,2.24,-5,5s2.24,5,5,5h4v-1.9H7c-1.71,0,-3.1,-1.39,-3.1,-3.1ZM8,13h8v-2H8v2ZM17,7h-4v1.9h4c1.71,0,3.1,1.39,3.1,3.1s-1.39,3.1,-3.1,3.1h-4V17h4c2.76,0,5,-2.24,5,-5s-2.24,-5,-5,-5Z") }
    val Star: ImageVector by lazy { icon("Star", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2L9.19,8.63L2,9.24l5.46,4.73L5.82,21Z") }
    val StarBorder: ImageVector by lazy { icon("StarBorder", "M22,9.24l-7.19,-0.62L12,2L9.19,8.63L2,9.24l5.46,4.73L5.82,21L12,17.27L18.18,21l-1.63,-7.03L22,9.24ZM12,15.4l-3.76,2.27l1,-4.28l-3.32,-2.88l4.38,-0.38L12,6.1l1.71,4.04l4.38,0.38l-3.32,2.88l1,4.28L12,15.4Z") }
    val Keyboard: ImageVector by lazy { icon("Keyboard", "M20,5H4c-1.1,0,-1.99,0.9,-1.99,2L2,17c0,1.1,0.9,2,2,2h16c1.1,0,2,-0.9,2,-2V7c0,-1.1,-0.9,-2,-2,-2ZM11,8h2v2h-2V8ZM11,11h2v2h-2v-2ZM8,8h2v2H8V8ZM8,11h2v2H8v-2ZM7,13H5v-2h2v2ZM7,10H5V8h2v2ZM16,17H8v-2h8v2ZM16,13h-2v-2h2v2ZM16,10h-2V8h2v2ZM19,13h-2v-2h2v2ZM19,10h-2V8h2v2Z") }
    val VolumeUp: ImageVector by lazy { icon("VolumeUp", "M3,9v6h4l5,5V4L7,9H3ZM16.5,12c0,-1.77,-1.02,-3.29,-2.5,-4.03v8.05c1.48,-0.73,2.5,-2.25,2.5,-4.02ZM14,3.23v2.06c2.89,0.86,5,3.54,5,6.71s-2.11,5.85,-5,6.71v2.06c4.01,-0.91,7,-4.49,7,-8.77s-2.99,-7.86,-7,-8.77Z") }
    val Warning: ImageVector by lazy { icon("Warning", "M1,21h22L12,2L1,21ZM13,18h-2v-2h2v2ZM13,14h-2v-4h2v4Z") }
    val File: ImageVector by lazy { icon("File", "M6,2c-1.1,0,-1.99,0.9,-1.99,2L4,20c0,1.1,0.89,2,1.99,2H18c1.1,0,2,-0.9,2,-2V8l-6,-6H6ZM13,9V3.5L18.5,9H13Z") }
    val Image: ImageVector by lazy { icon("Image", "M21,19V5c0,-1.1,-0.9,-2,-2,-2H5c-1.1,0,-2,0.9,-2,2v14c0,1.1,0.9,2,2,2h14c1.1,0,2,-0.9,2,-2ZM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5Z") }

    private fun icon(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
    }.build()
}
