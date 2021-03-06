(ns csgo-demo-reader.core
  (:require [clojure.java.io :as io]
            [octet.core :as buf]
            [csgo-demo-reader.spec :as spec]
            [csgo-demo-reader.util :as util])
  (:use [flatland.protobuf.core])
  (:import (com.valve NetmessagesPublic$CNETMsg_NOP NetmessagesPublic$CNETMsg_Disconnect NetmessagesPublic$CNETMsg_File
                      NetmessagesPublic$CNETMsg_Tick NetmessagesPublic$CNETMsg_StringCmd NetmessagesPublic$CNETMsg_SetConVar
                      NetmessagesPublic$CNETMsg_SignonState NetmessagesPublic$CSVCMsg_ServerInfo NetmessagesPublic$CSVCMsg_SendTable
                      NetmessagesPublic$CSVCMsg_ClassInfo NetmessagesPublic$CSVCMsg_SetPause NetmessagesPublic$CSVCMsg_CreateStringTable
                      NetmessagesPublic$CSVCMsg_UpdateStringTable NetmessagesPublic$CSVCMsg_VoiceInit NetmessagesPublic$CSVCMsg_VoiceData
                      NetmessagesPublic$CSVCMsg_Print NetmessagesPublic$CSVCMsg_Sounds NetmessagesPublic$CSVCMsg_SetView
                      NetmessagesPublic$CSVCMsg_FixAngle NetmessagesPublic$CSVCMsg_CrosshairAngle NetmessagesPublic$CSVCMsg_BSPDecal
                      NetmessagesPublic$CSVCMsg_UserMessage NetmessagesPublic$CSVCMsg_GameEvent NetmessagesPublic$CSVCMsg_PacketEntities
                      NetmessagesPublic$CSVCMsg_TempEntities NetmessagesPublic$CSVCMsg_Prefetch NetmessagesPublic$CSVCMsg_Menu
                      NetmessagesPublic$CSVCMsg_GameEventList NetmessagesPublic$CSVCMsg_GetCvarValue)))

(def unknown-msg-count (atom 0))

(def NOP (protodef NetmessagesPublic$CNETMsg_NOP))
(def Disconnect (protodef NetmessagesPublic$CNETMsg_Disconnect))
(def File (protodef NetmessagesPublic$CNETMsg_File))
(def Tick (protodef NetmessagesPublic$CNETMsg_Tick))
(def StringCmd (protodef NetmessagesPublic$CNETMsg_StringCmd))
(def SetConVar (protodef NetmessagesPublic$CNETMsg_SetConVar))
(def SignonState (protodef NetmessagesPublic$CNETMsg_SignonState))
(def ServerInfo (protodef NetmessagesPublic$CSVCMsg_ServerInfo))
(def SendTable (protodef NetmessagesPublic$CSVCMsg_SendTable))
(def ClassInfo (protodef NetmessagesPublic$CSVCMsg_ClassInfo))
(def SetPause (protodef NetmessagesPublic$CSVCMsg_SetPause))
(def CreateStringTable (protodef NetmessagesPublic$CSVCMsg_CreateStringTable))
(def UpdateStringTable (protodef NetmessagesPublic$CSVCMsg_UpdateStringTable))
(def VoiceInit (protodef NetmessagesPublic$CSVCMsg_VoiceInit))
(def VoiceData (protodef NetmessagesPublic$CSVCMsg_VoiceData))
(def Print (protodef NetmessagesPublic$CSVCMsg_Print))
(def Sounds (protodef NetmessagesPublic$CSVCMsg_Sounds))
(def SetView (protodef NetmessagesPublic$CSVCMsg_SetView))
(def FixAngle (protodef NetmessagesPublic$CSVCMsg_FixAngle))
(def CrosshairAngle (protodef NetmessagesPublic$CSVCMsg_CrosshairAngle))
(def BSPDecal (protodef NetmessagesPublic$CSVCMsg_BSPDecal))
(def UserMessage (protodef NetmessagesPublic$CSVCMsg_UserMessage))
(def GameEvent (protodef NetmessagesPublic$CSVCMsg_GameEvent))
(def PacketEntities (protodef NetmessagesPublic$CSVCMsg_PacketEntities))
(def TempEntities (protodef NetmessagesPublic$CSVCMsg_TempEntities))
(def Prefetch (protodef NetmessagesPublic$CSVCMsg_Prefetch))
(def Menu (protodef NetmessagesPublic$CSVCMsg_Menu))
(def GameEventList (protodef NetmessagesPublic$CSVCMsg_GameEventList))
(def GetCvarValue (protodef NetmessagesPublic$CSVCMsg_GetCvarValue))

(def commands
  {0 NOP
   1 Disconnect
   2 File
   4 Tick
   5 StringCmd
   6 SetConVar
   7 SignonState
   8 ServerInfo
   9 SendTable
   10 ClassInfo
   11 SetPause
   12 CreateStringTable
   13 UpdateStringTable
   14 VoiceInit
   15 VoiceData
   16 Print
   17 Sounds
   18 SetView
   19 FixAngle
   20 CrosshairAngle
   21 BSPDecal
   23 UserMessage
   25 GameEvent
   26 PacketEntities
   27 TempEntities
   28 Prefetch
   29 Menu
   30 GameEventList
   31 GetCvarValue})

(defn skip-raw-data [input-stream]
  (let [size-buf (buf/allocate (buf/size spec/int32LE))]
    (.read input-stream (.array size-buf))
    (let [size (long (buf/read size-buf spec/int32LE))]
      (util/safe-skip input-stream size))))

(defn read-raw-data [input-stream]
  (let [size-buf (buf/allocate (buf/size spec/int32LE))]
    (.read input-stream (.array size-buf))
    (let [size (buf/read size-buf spec/int32LE)
          b (byte-array size)]
      (util/safe-read input-stream b))))

(defn read-demo-header [input-stream]
  (let [b (buf/allocate (buf/size spec/demo-header))]
    (.read input-stream (.array b))
    (buf/read b spec/demo-header)))

(defn read-cmd-header [input-stream]
  (let [b (buf/allocate (buf/size spec/cmd-header))]
    (.read input-stream (.array b))
    (buf/read b spec/cmd-header)))

(defn read-demo-cmd-info [input-stream]
  (let [b (buf/allocate (buf/size spec/demo-cmd-info))]
    (.read input-stream (.array b))
    (buf/read b spec/demo-cmd-info)))

(defn read-sequence-info [input-stream]
  (let [b (buf/allocate (buf/size spec/sequence-info))]
    (.read input-stream (.array b))
    (buf/read b spec/sequence-info)))

(defn read-packet-size [input-stream]
  (let [size-buf (buf/allocate (buf/size spec/int32LE))]
    (.read input-stream (.array size-buf))
    (buf/read size-buf spec/int32LE)))

(defn ret-inc-pos [pos v]
  (apply conj [(+ pos (first v))] (rest v)))

(defn read-demo-packet [input-stream {packet-cmd-fns :packet-cmds :as handler-fns :or {packet-cmd-fns {}}}]
  (let [packet-size (read-packet-size input-stream)]
    (loop [num-bytes-read 0]
      (if (<= packet-size num-bytes-read)
        num-bytes-read
        (let [[num-bytes-read cmd] (ret-inc-pos num-bytes-read (spec/read-var-int32 input-stream))
              [num-bytes-read size] (ret-inc-pos num-bytes-read (spec/read-var-int32 input-stream))
              cmd-proto (get commands cmd)
              cmd-fn (get packet-cmd-fns cmd)
              num-bytes-read (+ num-bytes-read size)]
          (if (and cmd-proto cmd-fn)
            (let [b (buf/allocate size)]
              (.read input-stream (.array b))
              (cmd-fn (protobuf-load cmd-proto (.array b)) handler-fns))
            (do
              (swap! unknown-msg-count inc)
              (util/safe-skip input-stream size)))
          (recur num-bytes-read))))))

(defn read-data-tables [input-stream]
  (skip-raw-data input-stream))

(defn read-string-tables [input-stream]
  (skip-raw-data input-stream))

(defn read-console-cmd [input-stream]
  (skip-raw-data input-stream))

(defn read-user-cmd [input-stream]
  (skip-raw-data input-stream))

(defn handle-demo-packet [input-stream handler-fns]
  (read-demo-cmd-info input-stream)
  (read-sequence-info input-stream)
  (read-demo-packet input-stream handler-fns))

(defn read-demo-cmds [input-stream handler-fns]
  (loop []
    (let [cmd-header (read-cmd-header input-stream)]
      (cond
        (or (= (:cmd cmd-header) 1) (= (:cmd cmd-header) 2)) (do
                                                               (handle-demo-packet input-stream handler-fns)
                                                               (recur))
        (= (:cmd cmd-header) 3) (recur)
        (= (:cmd cmd-header) 4) (do
                                  (read-console-cmd input-stream)
                                  (recur))
        (= (:cmd cmd-header) 5) (do
                                  (read-user-cmd input-stream)
                                  (recur))
        (= (:cmd cmd-header) 6) (do
                                  (read-data-tables input-stream)
                                  (recur))
        (= (:cmd cmd-header) 7) nil
        (= (:cmd cmd-header) 8) (throw (UnsupportedOperationException. "Cannot parse customdata"))
        (= (:cmd cmd-header) 9) (do
                                  (read-string-tables input-stream)
                                  (recur))
        :else (throw (UnsupportedOperationException. (str "Unknown command: " (:cmd cmd-header))))))))

(def event-types
  {1 :val-string
   2 :val-float
   3 :val-long
   4 :val-short
   5 :val-byte
   6 :val-bool
   7 :val-wstring})

; {eventid {:eventid 0 :name name :keys [{:type 1 :name name} ...]}}
(defn parse-game-event-list [game-event-list-cmd]
  (reduce #(assoc %1 (:eventid %2) %2) {} (:descriptors game-event-list-cmd)))

;{:eventid 0 :keys [{:type 1 :val-string "stuff"}...]} -> {:eventid 0 :name name :data {name "stuff" ...}}
(defn parse-game-event [game-event-cmd game-event-list]
  (if-let [game-event-info (get game-event-list (:eventid game-event-cmd))]
    (let [fields (map vector (:keys game-event-info) (:keys game-event-cmd))
          base-game-event {:eventid (:eventid game-event-cmd) :name (:name game-event-info) :data {}}]
      (reduce (fn [game-event [{:keys [type name]} val]]
                (assoc-in game-event [:data name] (get val (get event-types type)))) base-game-event fields))
    (throw (RuntimeException. "Unknown event " (:eventid game-event-cmd)))))

(defn handle-game-event [game-event-cmd game-event-list {game-event-fns :game-events :or {game-event-fns {}} :as handler-fns}]
  (let [game-event (parse-game-event game-event-cmd game-event-list)]
    (if-let [game-event-fn (get game-event-fns (:name game-event))]
      (game-event-fn game-event))))

(defn create-game-event-list-handler [game-event-list-atom]
  (fn [game-event-list-cmd handler-fns]
    (swap! game-event-list-atom merge (parse-game-event-list game-event-list-cmd))))

(defn create-game-event-handler [game-event-list-atom]
  (fn [game-event-cmd handler-fns]
    (handle-game-event game-event-cmd @game-event-list-atom handler-fns)))

(defn read-demo
  ([fname]
   (read-demo fname {:demo-header println :packet-cmds (reduce #(assoc %1 %2 (fn [cmd fns] (println cmd))) {} (keys commands))}))
  ([fname {demo-header-fn :demo-header :as handler-fns}]
   (with-open [is (io/input-stream fname)]
     (demo-header-fn (read-demo-header is))
     (read-demo-cmds is handler-fns))))
