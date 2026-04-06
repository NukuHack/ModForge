<?xml version="1.0" encoding="us-ascii"?>
<AnimDB FragDef="Animations/Mannequin/ADB/kcd_male_fragmentids.xml" TagDef="Animations/Mannequin/ADB/kcd_male_tags.xml">
	<FragmentList>
		<ADLG_Cameras>
			<Fragment BlendOutDuration="0.2" GUID="89875e25-8ac5-8fe0-ca3d-653572dafd03" Tags="sitting+drunk">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="ad_cam_base_bench_drunk_add" flags="Loop" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="89875e25-8ac5-8fe0-ca3d-653572dafd2f" Tags="sitting">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="ad_cam_base_bench_add" flags="Loop" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="916d326d-da4f-f64f-523b-57f7e467320c" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="aec1e30c-1fcb-f5a2-6ce8-c44c1d2f1630" Tags="sittingNoTable">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="ad_cam_base_bench_add" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</ADLG_Cameras>
		<ADLG_KneelNoSword_in>
			<Fragment BlendOutDuration="0.2" GUID="0c662102-84d0-4b9a-8bee-9a745358c5aa" Tags="">
				<ProcLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Procedural type="" />
				</ProcLayer>
			</Fragment>
		</ADLG_KneelNoSword_in>
		<ADLG_Quest_PickMagicArrowFail>
			<Fragment BlendOutDuration="0.2" GUID="5623064c-fd5a-4ace-98d0-a5b87379e4f9" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="quest_pick_magic_arrow_fail" />
				</AnimLayer>
			</Fragment>
		</ADLG_Quest_PickMagicArrowFail>
		<ADLG_Quest_PickMagicArrowShovel>
			<Fragment BlendOutDuration="0.2" GUID="2a83e4f5-6dfe-4ce6-a294-0fe254c9fbaf" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="quest_pick_magic_arrow_shovel" />
				</AnimLayer>
			</Fragment>
		</ADLG_Quest_PickMagicArrowShovel>
		<ADLG_Quest_PickMagicArrowSucces>
			<Fragment BlendOutDuration="0.2" GUID="4cdd633f-87de-4aa2-8502-6c80afcc5747" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="quest_pick_magic_arrow_succes" />
				</AnimLayer>
			</Fragment>
		</ADLG_Quest_PickMagicArrowSucces>
		<BathDialog>
			<Fragment BlendOutDuration="0.2" GUID="629b1ea4-884c-8cb5-f7b7-8fe6683fc072" Tags="bath">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</BathDialog>
		<CarryItemPickup>
			<?Fragment BlendOutDuration="0.2" GUID="170b64fc-e6ae-4afc-ad65-e883b810af6a" Tags="" FragTags="PackAndPick">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0.5" Duration="0.2" />
					<Animation name="quest_collection_carcasses" speed="1.1" />
				</AnimLayer>
			</Fragment?>
		</CarryItemPickup>
		<CombatAttackMercy>
			<Fragment BlendOutDuration="0.2" GUID="08d29697-6aab-9204-6a13-b667995099ba" Tags="l_dagger+r_longsword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_shsw_02" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="0b2b12ad-52fe-2aea-dbbb-35cb877e4c9e" Tags="r_sabre" FragTags="stab+attack_special">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_axe_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="0cba04eb-7395-40c2-32fd-74bf51e1bb99" Tags="r_mace" FragTags="smash">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_axe_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="1eebf1dc-de10-737a-df9b-93a4250ecaf4" Tags="r_longsword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_lngsw_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="27d583f6-6f54-0b94-cf8d-4d97cb6f37b0" Tags="r_sword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_shsw_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="2b945f50-51dc-3640-51e8-fd800485a04c" Tags="l_torch+r_longsword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_shsw_02" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="42d44aaf-23a7-6770-897f-1d367b68b8df" Tags="l_dagger+r_sabre" FragTags="smash+attack_special">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="combat_finish_shsw_02" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="5d9588e6-e61d-4fcd-8954-25603827aa74" Tags="" FragTags="stab+attack_special+r_shortSwords">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="combat_finish_shsw_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="62bdbf89-e61b-475e-760f-cba17d43a726" Tags="l_torch+r_longsword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_shsw_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="6e9588e6-e61d-4fcd-8954-25603827aa74" Tags="" FragTags="stab+attack_special+r_shortSwords">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="combat_finish_shsw_02" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="7783b9b6-4d46-d9d3-a1ce-eeb1c3ebc476" Tags="r_longsword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_lngsw_02" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="7975d6a4-9688-a5af-c08d-ae08648f4a2d" Tags="l_dagger+r_longsword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_shsw_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="867a2214-1849-bda7-d225-943c43db22d2" Tags="r_axe">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_axe_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="86967f82-5bed-8156-bb84-8755e4b18106" Tags="l_shield+r_sabre" FragTags="stab+attack_special">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_axe_shld_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="9469973f-7ee8-bf6e-2da4-b3af14ae0fd3" Tags="l_shield+r_sword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_shsw_shld_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="95971e7d-fd6c-e0b9-01d1-cb2cbecddab6" Tags="l_noweapon+r_noweapon" FragTags="smash+attack_special">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_nw_break_neck_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="a3be088e-25d8-85e7-e48a-251da8b7b135" Tags="r_mace" FragTags="smash">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_axe_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="b36829c8-02bb-a190-d5fa-558b0e38f52c" Tags="r_sword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_shsw_02" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="bc5b464b-c87a-3537-4496-577bdc7ce474" Tags="r_sabre" FragTags="stab+attack_special">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_axe_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="c80fc918-6da1-46c5-9f58-be17bfcef027" Tags="l_shield" FragTags="stab+attack_special+r_swords">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="combat_finish_shsw_shld_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="cd297cc0-27eb-90d1-e2e0-180713e8c38d" Tags="l_shield+r_axe">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_axe_shld_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="da0d58d5-32cb-292a-b4ba-4f7307efb572" Tags="l_shield+r_longsword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="combat_finish_shsw_01" />
				</AnimLayer>
			</Fragment>
			<Fragment BlendOutDuration="0.2" GUID="eb81ae2b-216e-a412-c437-ddccba3ffc02" Tags="l_dagger+r_sabre" FragTags="smash+attack_special">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="combat_finish_shsw_01" />
				</AnimLayer>
			</Fragment>
		</CombatAttackMercy>
		<CookingPanCamp>
			<?Fragment BlendOutDuration="0.2" GUID="5e764088-1812-41e9-afe6-424b535a6938" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="1.29" />
					<Animation name="behavior_cooking_pan_camp_player" />
				</AnimLayer>
			</Fragment?>
		</CookingPanCamp>
		<CookingPanKitchen>
			<?Fragment BlendOutDuration="0.2" GUID="ed543b77-3c45-49f3-893b-755320f56149" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="behavior_cooking_pan_kitchen_player" />
				</AnimLayer>
			</Fragment?>
		</CookingPanKitchen>
		<Digging>
			<?Fragment BlendOutDuration="0.2" GUID="2f74adb9-e289-4b3c-aa57-b5e4aac164bf" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="grave_digging" flags="Loop" />
				</AnimLayer>
			</Fragment?>
		</Digging>
		<Drying>
			<?Fragment BlendOutDuration="0.2" GUID="854b6e48-673e-4e28-8c55-78af12f7799f" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="behavior_drying_player" />
				</AnimLayer>
			</Fragment?>
		</Drying>
		<FishingDialog>
			<Fragment BlendOutDuration="0.2" GUID="f9dcc45d-9728-a661-a91c-95c303ac9e0c" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</FishingDialog>
		<LaundryWash>
			<Fragment BlendOutDuration="0.2" GUID="15d52fee-b2af-46b4-9928-58b2d9379d9f" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="laundry_wash_player_03" />
				</AnimLayer>
			</Fragment>
		</LaundryWash>
		<Leaning_Back_Loop>
			<Fragment BlendOutDuration="0.2" GUID="e7d65987-7e7b-4bee-b388-15902d4e3165" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</Leaning_Back_Loop>
		<Leaning_Left_Loop>
			<Fragment BlendOutDuration="0.2" GUID="13b6f6ee-57d7-475c-a230-f8ecbdae7124" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</Leaning_Left_Loop>
		<Leaning_Right_Loop>
			<Fragment BlendOutDuration="0.2" GUID="9ad5a49a-4f87-4916-8447-160fdf4cb9f2" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</Leaning_Right_Loop>
		<MotionIdle>
			<Fragment BlendOutDuration="0.2" GUID="28b9866b-9199-6001-ad82-e97660ca5a9a" Tags="player">
				<AnimLayer />
			</Fragment>
		</MotionIdle>
		<PickingHerbs>
			<?Fragment BlendOutDuration="0.2" GUID="157e1385-b7d2-c099-8ec5-84d27746b924" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0.6" Duration="0.05" />
					<Animation name="herbs_picking_area_in" />
					<Blend ExitTime="-1" StartTime="0" Duration="0.5" />
					<Animation name="herbs_picking_area_loop" flags="Loop" />
				</AnimLayer>
			</Fragment?>
		</PickingHerbs>
		<PickingRose>
			<?Fragment BlendOutDuration="0.2" GUID="afa6d7e4-5d46-4cce-ba69-9bf1c700be08" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0.25" Duration="0.2" />
					<Animation name="herbs_picking_area_in" />
					<Blend ExitTime="0.8499999" StartTime="0" Duration="0.5" />
					<Animation name="herbs_picking_area_out" />
				</AnimLayer>
			</Fragment?>
		</PickingRose>
		<SharpeningDialog>
			<Fragment BlendOutDuration="0.2" GUID="2ad63a6e-818d-af01-2927-dd97164cfd91" Tags="r_longsword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</SharpeningDialog>
		<SharpeningDialog_VAR>
			<Fragment BlendOutDuration="0.2" GUID="cac6b3cc-b42b-7425-d3b3-cfe682d3b556" Tags="r_longsword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</SharpeningDialog_VAR>
		<ShortButchering>
			<?Fragment BlendOutDuration="0.2" GUID="deb43566-fe8e-46e7-a866-d9b9b9461451" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="short_butchering" />
				</AnimLayer>
			</Fragment?>
		</ShortButchering>
		<SmithForgingDialog>
			<Fragment BlendOutDuration="0.2" GUID="81776f28-92cb-b4a0-04d9-95d3829b1d9a" Tags="" FragTags="smithItemSword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</SmithForgingDialog>
		<SmithForgingDialog_VAR>
			<Fragment BlendOutDuration="0.2" GUID="5d1f755f-19ad-862c-3afc-145da3ecc126" Tags="" FragTags="smithItemSword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</SmithForgingDialog_VAR>
		<SmithHeatingDialog>
			<Fragment BlendOutDuration="0.2" GUID="0aa3a4ef-7eba-0970-e53e-856546b30de2" Tags="" FragTags="smithItemSword">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</SmithHeatingDialog>
		<SmithHeatingDialog_VAR>
			<Fragment BlendOutDuration="0.2" GUID="8a5bf654-7be2-99ba-9b8c-d674b158b528" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</SmithHeatingDialog_VAR>
		<SmokehouseSniff>
			<?Fragment BlendOutDuration="0.2" GUID="784dc891-e4b5-491f-af08-dc35d98fe238" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0.2" />
					<Animation name="behavior_smokehouse_sniff_player" />
				</AnimLayer>
			</Fragment?>
		</SmokehouseSniff>
		<SpillPotionTub>
			<Fragment BlendOutDuration="0.2" GUID="8710339c-d491-75d2-9fa7-5944535b2199" Tags="">
				<AnimLayer />
			</Fragment>
		</SpillPotionTub>
		<StomachPainIdle>
			<Fragment BlendOutDuration="0.2" GUID="5bd94f79-596f-e51a-b40d-b25893fdaf2a" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</StomachPainIdle>
		<StomachPainIdleDialog>
			<Fragment BlendOutDuration="0.2" GUID="38d067b0-9e40-05fd-462f-c3dfe82a9022" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</StomachPainIdleDialog>
		<Sweeping>
			<Fragment BlendOutDuration="0.2" GUID="ae12b4f4-9b5f-4214-8b48-018d85b06504" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="male_housekeeper_sweeping" />
				</AnimLayer>
			</Fragment>
		</Sweeping>
		<SweepingFloorDialog>
			<Fragment BlendOutDuration="0.2" GUID="7cf1e3fe-5dbc-8145-5e65-df8975b53485" Tags="">
				<AnimLayer>
					<Blend ExitTime="0" StartTime="0" Duration="0" />
					<Animation name="3d_ad_cameras_base" flags="Loop" />
				</AnimLayer>
			</Fragment>
		</SweepingFloorDialog>
	</FragmentList>
</AnimDB>
