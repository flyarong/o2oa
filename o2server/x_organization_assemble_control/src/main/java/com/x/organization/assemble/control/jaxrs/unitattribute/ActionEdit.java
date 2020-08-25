package com.x.organization.assemble.control.jaxrs.unitattribute;

import com.x.base.core.project.cache.CacheManager;
import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonElement;
import com.x.base.core.container.EntityManagerContainer;
import com.x.base.core.container.factory.EntityManagerContainerFactory;
import com.x.base.core.entity.JpaObject;
import com.x.base.core.entity.annotation.CheckPersistType;
import com.x.base.core.project.bean.WrapCopier;
import com.x.base.core.project.bean.WrapCopierFactory;
import com.x.base.core.project.http.ActionResult;
import com.x.base.core.project.http.EffectivePerson;
import com.x.base.core.project.jaxrs.WoId;
import com.x.base.core.project.tools.ListTools;
import com.x.organization.assemble.control.Business;
import com.x.organization.core.entity.Unit;
import com.x.organization.core.entity.UnitAttribute;

class ActionEdit extends BaseAction {

	ActionResult<Wo> execute(EffectivePerson effectivePerson, String id, JsonElement jsonElement) throws Exception {
		try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {
			Wi wi = this.convertToWrapIn(jsonElement, Wi.class);
			Business business = new Business(emc);
			ActionResult<Wo> result = new ActionResult<>();
			UnitAttribute o = business.unitAttribute().pick(id);
			if (null == o) {
				throw new ExceptionUnitAttributeNotExist(id);
			}
			Unit unit = business.unit().pick(o.getUnit());
			if (null == unit) {
				throw new ExceptionUnitNotExist(o.getUnit());
			}
			if (!business.editable(effectivePerson, unit)) {
				throw new ExceptionDenyEditUnit(effectivePerson, unit.getName());
			}
			if (StringUtils.isEmpty(wi.getName())) {
				throw new ExceptionNameEmpty();
			}
			/** 如果唯一标识不为空,要检查唯一标识是否唯一 */
			if (uniqueDuplicateWhenNotEmpty(business, o)) {
				throw new ExceptionDuplicateUnique(o.getName(), o.getUnique());
			}
			if (this.duplicateOnUnit(business, unit, wi.getName(), o)) {
				throw new ExceptionDuplicateOnUnit(wi.getName(), unit.getName());
			}
			/** pick 出来的需要重新find */
			o = emc.find(o.getId(), UnitAttribute.class);
			Wi.copier.copy(wi, o);
			/** 如果唯一标识不为空,要检查唯一标识是否唯一 */
			if (uniqueDuplicateWhenNotEmpty(business, o)) {
				throw new ExceptionDuplicateUnique(o.getName(), o.getUnique());
			}
			o.setUnit(unit.getId());
			emc.beginTransaction(UnitAttribute.class);
			emc.check(o, CheckPersistType.all);
			emc.commit();
			CacheManager.notify(UnitAttribute.class);
			Wo wo = new Wo();
			wo.setId(o.getId());
			result.setData(wo);
			return result;
		}
	}

	public static class Wo extends WoId {
	}

	public static class Wi extends UnitAttribute {

		private static final long serialVersionUID = -7527954993386512109L;

		static WrapCopier<Wi, UnitAttribute> copier = WrapCopierFactory.wi(Wi.class, UnitAttribute.class, null,
				ListTools.toList(JpaObject.FieldsUnmodify, "pinyin", "pinyinInitial", "unit"));

	}

}