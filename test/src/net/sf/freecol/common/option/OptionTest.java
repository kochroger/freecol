/**
 *  Copyright (C) 2002-2024  The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.option;

import java.util.List;

import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.util.test.FreeColTestCase;


public class OptionTest extends FreeColTestCase {

    public void testGameOptions() {
        OptionGroup gameOptions = spec().getGameOptions();
        assertNotNull(gameOptions);
        assertFalse(gameOptions.getOptions().isEmpty());
        assertNotNull(spec().getOptionGroup(GameOptions.GAMEOPTIONS_MAP));
        assertNotNull(spec().getOptionGroup(GameOptions.GAMEOPTIONS_COLONY));
        assertTrue(spec().hasOption(GameOptions.FOG_OF_WAR,
                                    BooleanOption.class));
        assertTrue(spec().hasOption(GameOptions.ALLOW_STUDENT_SELECTION,
                                    BooleanOption.class));
        assertFalse(gameOptions.getBoolean(GameOptions.CUSTOM_IGNORE_BOYCOTT));
        assertFalse(spec().getBoolean(GameOptions.CUSTOM_IGNORE_BOYCOTT));
        assertFalse(spec().getBoolean(GameOptions.EXPERTS_HAVE_CONNECTIONS));
        assertFalse(spec().getBoolean(GameOptions.SAVE_PRODUCTION_OVERFLOW));
        assertTrue(spec().getBoolean(GameOptions.ALLOW_STUDENT_SELECTION));
    }

    public void testCloneIntegerOption() {
        IntegerOption money = spec().getOption(GameOptions.STARTING_MONEY,
                                               IntegerOption.class);
        IntegerOption money2 = money.cloneOption();

        assertFalse(money == money2);
        assertEquals(money.getId(), money2.getId());
        assertEquals(money.getValue(), money2.getValue());
        assertEquals(money.getMinimumValue(), money2.getMinimumValue());
        assertEquals(money.getMaximumValue(), money2.getMaximumValue());

        money2.setValue(money.getValue() + 23);
        assertEquals((int) (money.getValue() + 23), (int) money2.getValue());

    }

    public void testUnitListOption() {
        List<AbstractUnit> ref = spec().getUnitList(GameOptions.REF_FORCE);
        assertNotNull(ref);
        for (AbstractUnit unit : ref) {
            assertTrue(unit.getNumber() > 0);
            assertTrue(unit.getNumber() < Integer.MAX_VALUE);
        }

    }


    /**
     * OptionGroups are editable by default. If an OptionGroup is not
     * editable, however, none of its subgroups are editable either.
     */
    public void testInheritsEditable() {

        OptionGroup difficulties = spec().getOptionGroup(Specification.DIFFICULTY_LEVELS);
        assertNotNull(difficulties);
        assertTrue(difficulties.isEditable());

        String[] levels = new String[] { "veryEasy", "easy", "medium", "hard", "veryHard" };
        String[] names = new String[] { "immigration", "natives", "monarch", "government", "other" };
        for (String level : levels) {
            OptionGroup group = difficulties.getOptionGroup("model.difficulty." + level);
            assertNotNull("Failed to find difficulty level '" + level + "'", group);
            assertFalse("Difficulty level '" + level + "' should not be editable", group.isEditable());
            for (String name : names) {
                OptionGroup subGroup = group.getOptionGroup("model.difficulty." + name);
                assertNotNull("Failed to find option group '" + name + "' (" + level + ")", subGroup);
                assertFalse("Option group '" + name + "' in '" + level + "' should not be editable", subGroup.isEditable());
            }
        }

        OptionGroup group = difficulties.getOptionGroup("model.difficulty.custom");
        assertNotNull("Failed to find difficulty level 'custom'", group);
        assertTrue("Difficulty level 'custom' should be editable", group.isEditable());
        for (String name : names) {
            OptionGroup subGroup = group.getOptionGroup("model.difficulty." + name);
            assertNotNull("Failed to find option group '" + name + "' (custom)", subGroup);
            assertTrue("Option group '" + name + "' should be editable", subGroup.isEditable());
        }

    }

    /**
     * Read a select option from XML into an existing option, catching
     * the warnings of {@code SelectOption}.
     *
     * @param o The option.
     * @param xml Its new declaration.
     * @return The warnings logged while reading.
     */
    private static List<String> readSelect(SelectOption o, String xml)
        throws Exception {
        final List<String> warnings = new java.util.ArrayList<>();
        final java.util.logging.Logger log
            = java.util.logging.Logger.getLogger(SelectOption.class.getName());
        final java.util.logging.Handler h = new java.util.logging.Handler() {
                @Override public void publish(java.util.logging.LogRecord r) {
                    if (r.getLevel().intValue()
                        >= java.util.logging.Level.WARNING.intValue()) {
                        warnings.add(r.getMessage());
                    }
                }
                @Override public void flush() {}
                @Override public void close() {}
            };
        log.addHandler(h);
        try (net.sf.freecol.common.io.FreeColXMLReader xr
             = new net.sf.freecol.common.io.FreeColXMLReader(
                 new java.io.StringReader(xml))) {
            xr.nextTag();
            o.readFromXML(xr);
        } finally {
            log.removeHandler(h);
        }
        return warnings;
    }

    private static String selectXml(String attributes, int values) {
        StringBuilder sb = new StringBuilder("<selectOption id=\"model.option.test\" "
            + attributes + " localizedLabels=\"false\">");
        for (int i = 0; i < values; i++) {
            sb.append("<selectValue value=\"").append(i)
                .append("\" label=\"v").append(i).append("\"/>");
        }
        return sb.append("</selectOption>").toString();
    }

    /**
     * A select option declared again with more values (the levi rules'
     * landGeneratorType, part P1: classic's four generators and the
     * original's fifth, default 4) takes its new default without a
     * warning: the value is checked against the values that follow the
     * attributes, not against the earlier declaration's.  A value that
     * is not among the new values still warns and falls back to the
     * first, as before.
     */
    public void testSelectOptionDeclaredAgain() throws Exception {
        final SelectOption o = new SelectOption("model.option.test", spec());
        assertTrue(readSelect(o, selectXml("defaultValue=\"1\"", 4)).isEmpty());
        assertEquals(4, o.getItemValues().size());
        assertEquals(1, (int)o.getValue());

        // Declared again with a fifth value as its default: 4, quietly.
        List<String> w = readSelect(o, selectXml("defaultValue=\"4\"", 5));
        assertEquals(w.toString(), 0, w.size());
        assertEquals(5, o.getItemValues().size());
        assertEquals(4, (int)o.getValue());

        // Declared again with a value it does not have: one warning, the
        // first value.
        w = readSelect(o, selectXml("defaultValue=\"7\"", 3));
        assertEquals(w.toString(), 1, w.size());
        assertTrue(w.get(0), w.get(0).contains("invalid value: 7"));
        assertEquals(3, o.getItemValues().size());
        assertEquals(0, (int)o.getValue());

        // A saved value is kept the same way.
        w = readSelect(o, selectXml("value=\"2\" defaultValue=\"0\"", 3));
        assertEquals(w.toString(), 0, w.size());
        assertEquals(2, (int)o.getValue());
    }
}
